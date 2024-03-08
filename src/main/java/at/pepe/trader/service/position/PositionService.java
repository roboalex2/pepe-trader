package at.pepe.trader.service.position;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.model.OrderPojo;
import at.pepe.trader.model.Position;
import at.pepe.trader.model.PositionStatus;
import at.pepe.trader.persistent.PositionRepositoryImpl;
import at.pepe.trader.service.binance.OrderService;
import at.pepe.trader.service.candle.BarSeriesHolderService;
import com.binance.connector.client.SpotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final TradeConfigProperties tradeConfigProperties;
    private final PositionRepositoryImpl positionRepository;
    private final OrderService orderService;
    private final BarSeriesHolderService barSeriesHolderService;

    private Map<Long, Position> positions = new ConcurrentHashMap<>();

    public void openPosition(BigDecimal price) {
        if (Optional.ofNullable(positions.values())
                .orElse(List.of()).stream()
                .anyMatch(pos ->
                        !Set.of(PositionStatus.FINISHED, PositionStatus.CANCELLED).contains(pos.getStatus()) &&
                                pos.getOpenAtPrice().equals(price))
        ) {
            return;
        }

        orderService.createNewOrder(
                price,
                tradeConfigProperties.getBaseAssetQuantityPerTrade().setScale(tradeConfigProperties.getBaseAssetScale(), RoundingMode.DOWN)
                        .divide(price, RoundingMode.UP).setScale(tradeConfigProperties.getQuoteAssetScale(), RoundingMode.DOWN),
                "BUY",
                new Random().nextLong()
        );
    }

    @Scheduled(cron = "*/20 * * * * *")
    private void cancelOldPositions() {
        BigDecimal currentPrice = (BigDecimal) barSeriesHolderService.getSecondSeries().getLastBar().getClosePrice().getDelegate();
        BigDecimal baseAssetToNoDeciConv = new BigDecimal(10).pow(tradeConfigProperties.getBaseAssetScale());
        List<Position> list = positions.values().stream()
                .filter(pos -> PositionStatus.WAITING_FOR_OPEN.equals(pos.getStatus()))
                .filter(pos -> pos.getCreatedAt().isBefore(Instant.now().atOffset(ZoneOffset.UTC).minusMinutes(1)))
                .filter(pos -> Math.abs(
                        currentPrice.multiply(baseAssetToNoDeciConv)
                                .subtract(
                                        pos.getOpenAtPrice().multiply(baseAssetToNoDeciConv)
                                ).doubleValue()
                ) >= 3)
                .toList();
        list.forEach(pos ->
                orderService.cancelOrder(pos.getOrderIdOpen())
        );

        if (!list.isEmpty()) {
            log.info("Cleared {} old orders.", list.size());
        }
    }

    public void onOrderUpdateEvent(OrderPojo order) {
        try {
            if ("NEW".equals(order.getOrderStatus()) && "BUY".equals(order.getAction())) {
                waitForOpenPosition(order);
            } else if ("FILLED".equals(order.getOrderStatus()) && "BUY".equals(order.getAction())) {
                if (order.getQuantity().subtract(order.getExecutedQty()).abs().doubleValue() <= 0.001) {
                    onPositionOpenFilled(order);
                } else {
                    log.warn("Slippery on order {}", order);
                }
            } else if ("CANCELED".equals(order.getOrderStatus()) && "BUY".equals(order.getAction())) {
                onPositionOpenCancelled(order);
            } else if ("NEW".equals(order.getOrderStatus()) && "SELL".equals(order.getAction())) {
                waitForClosePosition(order);
            } else if ("FILLED".equals(order.getOrderStatus()) && "SELL".equals(order.getAction())) {
                if (order.getQuantity().subtract(order.getExecutedQty()).abs().doubleValue() <= 0.001) {
                    onPositionCloseFilled(order);
                } else {
                    log.warn("Slippery on order {}", order);
                }
            } else if ("CANCELED".equals(order.getOrderStatus()) && "SELL".equals(order.getAction())) {
                onPositionOpenCancelled(order);
            }
        } catch (NumberFormatException e) {
            // Do nothing
        } catch (Exception e) {
            log.warn("Exception on order event.", e);
        }
    }

    private void onPositionOpenFilled(OrderPojo order) {
        Position position = getPosition(order);
        if (position != null && PositionStatus.WAITING_FOR_OPEN.equals(position.getStatus())) {
            position.setStatus(PositionStatus.OPENED);
            position.setOpenAtPrice(order.getPrice());
            orderService.createNewOrder(position.getCloseAtPrice(), position.getQuantityClose(), "SELL", position.getId());
            positionRepository.save(position.getId(), position);
            if (order.getCommissionAmount().doubleValue() > 0) {
                log.warn("We just had costs: " + order);
            }
            log.debug(position.toString());
        }
    }

    private void onPositionOpenCancelled(OrderPojo order) {
        Position position = getPosition(order);
        if (position == null) {
            return;
        }

        PositionStatus status = position.getStatus();
        if (Set.of(PositionStatus.WAITING_FOR_OPEN, PositionStatus.WAITING_FOR_CLOSE).contains(status)) {
            position.setStatus(PositionStatus.CANCELLED);
            positionRepository.save(position.getId(), position);
            if (PositionStatus.WAITING_FOR_CLOSE.equals(status)) {
                log.info(position.toString());
            }
            log.debug(position.toString());
        }
    }

    private void onPositionCloseFilled(OrderPojo order) {
        Position position = getPosition(order);
        if (position != null && PositionStatus.WAITING_FOR_CLOSE.equals(position.getStatus())) {
            position.setStatus(PositionStatus.FINISHED);
            position.setCloseAtPrice(order.getPrice());
            positionRepository.save(position.getId(), position);
            log.info(position.toString());
            if (order.getCommissionAmount().doubleValue() > 0) {
                log.warn("We just had costs: " + order);
            }
            double profit = position.getCloseAtPrice().multiply(position.getQuantityClose())
                    .subtract(
                            position.getOpenAtPrice().multiply(position.getQuantityOpen())
                    ).doubleValue();

            log.info("Profit: {}", profit);
        }
    }

    private void waitForOpenPosition(OrderPojo orderPojo) {
        long id = Long.parseLong(orderPojo.getClientOrderId().split("_")[0]);
        if (positions.containsKey(id)) {
            return;
        }
        BigDecimal baseAssetToNoDeciConv = new BigDecimal(10).pow(tradeConfigProperties.getBaseAssetScale());

        Position position = Position.builder()
                .orderIdOpen(orderPojo.getOrderId())
                .openAtPrice(orderPojo.getPrice())
                .closeAtPrice(orderPojo.getPrice().add(new BigDecimal(4).setScale(tradeConfigProperties.getBaseAssetScale(), RoundingMode.HALF_UP).divide(baseAssetToNoDeciConv,  RoundingMode.HALF_UP)))
                .quantityClose(orderPojo.getQuantity())
                .quantityOpen(orderPojo.getQuantity())
                .status(PositionStatus.WAITING_FOR_OPEN)
                .id(id)
                .createdAt(orderPojo.getCreatedAt())
                .build();
        positions.put(position.getId(), position);
        positionRepository.save(position.getId(), position);
        log.info(position.toString());
    }

    private void waitForClosePosition(OrderPojo order) {
        Position position = getPosition(order);
        if (position != null && PositionStatus.OPENED.equals(position.getStatus())) {
            position.setStatus(PositionStatus.WAITING_FOR_CLOSE);
            position.setOrderIdClose(order.getOrderId());
            positionRepository.save(position.getId(), position);
            log.info(position.toString());
        }
    }

    private Position getPosition(OrderPojo order) {
        long id = Long.parseLong(order.getClientOrderId().split("_")[0]);
        Position position = positions.get(id);
        if (position == null) {
            position = positionRepository.find(id);
            if (position != null) {
                positions.put(id, position);
            }
        }
        return position;
    }
}
