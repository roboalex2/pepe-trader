package at.pepe.trader.service.position;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.model.OrderPojo;
import at.pepe.trader.model.Position;
import at.pepe.trader.model.PositionStatus;
import at.pepe.trader.persistent.PositionRepositoryImpl;
import at.pepe.trader.service.binance.OrderService;
import at.pepe.trader.service.candle.BarSeriesHolderService;
import at.pepe.trader.service.discord.DiscordEmbedPublishingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class PositionService {

    private final TradeConfigProperties tradeConfigProperties;
    private final PositionRepositoryImpl positionRepository;
    private final OrderService orderService;
    private final BarSeriesHolderService barSeriesHolderService;
    private final DiscordEmbedPublishingService discordEmbedPublishingService;

    private Map<Long, Position> positions = new ConcurrentHashMap<>();

    private BigDecimal baseAssetToNoDeciConv;

    private int openedInCombo = 0;
    private int openComboResetCounter = 0;
    private final int MAX_POS_OVER_HOUR = 5;

    @Autowired
    public PositionService(
        TradeConfigProperties tradeConfigProperties,
        PositionRepositoryImpl positionRepository,
        OrderService orderService,
        BarSeriesHolderService barSeriesHolderService,
        DiscordEmbedPublishingService discordEmbedPublishingService
    ) {
        this.tradeConfigProperties = tradeConfigProperties;
        this.positionRepository = positionRepository;
        this.orderService = orderService;
        this.barSeriesHolderService = barSeriesHolderService;
        this.baseAssetToNoDeciConv = new BigDecimal(10).pow(tradeConfigProperties.getQuoteAssetScale());
        this.discordEmbedPublishingService = discordEmbedPublishingService;
    }


    public boolean openPosition(BigDecimal price) {
        // We only open the position if we have less than MAX_POS_OVER_HOUR unfinished positions in a row.
        // Meaning the counter reduces as soon as one closes or if more than 1 hour has passed since opening the last of the MAX_POS_OVER_HOUR it resets
        if (openedInCombo >= MAX_POS_OVER_HOUR) {
            return false;
        }

        if (Optional.ofNullable(positions.values())
            .orElse(List.of()).stream()
            .anyMatch(pos ->
                !Set.of(PositionStatus.FINISHED, PositionStatus.CANCELLED).contains(pos.getStatus()) &&
                    pos.getOpenAtPrice().equals(price))
        ) {
            return false;
        }

        if (!hasOpenOrderWaitingInProximity(price)) {
            orderService.createNewOrder(
                price,
                tradeConfigProperties.getQuoteAssetQuantityPerTrade().setScale(tradeConfigProperties.getQuoteAssetScale(), RoundingMode.DOWN)
                    .divide(price, RoundingMode.UP).setScale(tradeConfigProperties.getBaseAssetScale(), RoundingMode.DOWN),
                "BUY",
                new Random().nextLong()
            );
            return true;
        }
        return false;
    }

    private boolean hasOpenOrderWaitingInProximity(BigDecimal price) {
        return positions.values().stream()
            .filter(pos -> PositionStatus.WAITING_FOR_OPEN.equals(pos.getStatus()))
            .anyMatch(pos ->
                price.multiply(baseAssetToNoDeciConv)
                    .subtract(
                        pos.getOpenAtPrice().multiply(baseAssetToNoDeciConv)
                    ).doubleValue() <= 3);
    }

    @Scheduled(cron = "*/20 * * * * *")
    private void cancelOldPositions() {
        BigDecimal currentPrice = (BigDecimal) barSeriesHolderService.getSecondSeries().getLastBar().getClosePrice().getDelegate();

        // Cancel order when price rises by more than 2 points since creation of order.
        List<Position> list = positions.values().stream()
            .filter(pos -> PositionStatus.WAITING_FOR_OPEN.equals(pos.getStatus()))
            .filter(pos -> pos.getCreatedAt().isBefore(Instant.now().atOffset(ZoneOffset.UTC).minusMinutes(1)))
            .filter(pos -> Math.abs(
                currentPrice.multiply(baseAssetToNoDeciConv)
                    .subtract(
                        pos.getOpenAtPrice().multiply(baseAssetToNoDeciConv)
                    ).doubleValue()
            ) > (((double) tradeConfigProperties.getGapSizePoints() / 2d) + 1d))
            .toList();
        list.forEach(pos ->
            orderService.cancelOrder(pos.getOrderIdOpen())
        );

        if (!list.isEmpty()) {
            log.info("Cleared {} old orders.", list.size());
        }
    }

    @Scheduled(cron = "0 * * * * *")
    private void resetPositionInRowCounter() {
        if (openComboResetCounter >= 60) {
            openedInCombo = 0;
            openComboResetCounter = 0;
        } else if (openedInCombo >= (MAX_POS_OVER_HOUR - 1)) {
            openComboResetCounter++;
        } else {
            openComboResetCounter = 0;
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
            openedInCombo++;
            orderService.createNewOrder(position.getCloseAtPrice(), position.getQuantityClose(), "SELL", position.getId());
            positionRepository.save(position.getId(), position);
            if (order.getCommissionAmount().doubleValue() > 0) {
                log.warn("We just had costs: " + order);
                discordEmbedPublishingService.sendEmbed(
                    "Paid Commission!",
                    String.format("Newly opened position='%d'\n just had costs of %s\n", position.getId(), order.getCommissionAmount().toString()),
                    "#800080"
                );
            }
            discordEmbedPublishingService.sendEmbed(
                "Open " + position.getId(),
                String.format("Price: %s\nQuantity: %s\nUSD: %s $", position.getOpenAtPrice(), position.getQuantityOpen(), order.getPrice().multiply(order.getQuantity())),
                "#ADD8E6"
            );
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

            if (PositionStatus.WAITING_FOR_CLOSE.equals(status)) {
                log.info(position.toString());
                position.setClosedAt(order.getUpdatedAt());
                BigDecimal currentPrice = (BigDecimal) barSeriesHolderService.getSecondSeries().getLastBar().getClosePrice().getDelegate();
                discordEmbedPublishingService.sendEmbed(
                    "Cancelled Pos.: " + position.getId(),
                    String.format("OpenPrice: %s\nQuantity: %s\nOpenUSD: %s $\n CancelPrice: %s\n CancelUSD: %s $", position.getOpenAtPrice(), position.getQuantityOpen(), position.getOpenAtPrice().multiply(position.getQuantityOpen()), currentPrice, currentPrice.multiply(position.getQuantityOpen())),
                    "#800080"
                );
            }
            positionRepository.save(position.getId(), position);
            log.debug(position.toString());
        }
    }

    private void onPositionCloseFilled(OrderPojo order) {
        Position position = getPosition(order);
        if (position != null && PositionStatus.WAITING_FOR_CLOSE.equals(position.getStatus())) {
            position.setStatus(PositionStatus.FINISHED);
            position.setClosedAt(order.getUpdatedAt());
            position.setCloseAtPrice(order.getPrice());
            if (openedInCombo > 0) {
                openedInCombo--;
            }
            positionRepository.save(position.getId(), position);
            log.info(position.toString());
            if (order.getCommissionAmount().doubleValue() > 0) {
                log.warn("We just had costs: " + order);
                discordEmbedPublishingService.sendEmbed(
                    "Paid Commission!",
                    String.format("Newly opened position='%d'\n just had costs of %s\n", position.getId(), order.getCommissionAmount().toString()),
                    "#800080"
                );
            }

            BigDecimal openPriceUSD = position.getOpenAtPrice().multiply(position.getQuantityOpen());
            double profit = position.getCloseAtPrice().multiply(position.getQuantityClose())
                .subtract(openPriceUSD)
                .doubleValue();
            discordEmbedPublishingService.sendEmbed(
                "Close " + position.getId(),
                String.format("Quantity: %s\nOpenPrice: %s\nOpenUSD: %s $\nClosePrice: %s\nCloseUSD: %s $\nProfit: %f $",
                    position.getQuantityOpen(),
                    position.getOpenAtPrice(),
                    openPriceUSD,
                    position.getCloseAtPrice(),
                    position.getCloseAtPrice().multiply(position.getQuantityClose()),
                    profit
                ),
                profit > 0 ? "#50C878" : "#e0115f"
            );
            log.info("Profit: {}", profit);
        }
    }

    private void waitForOpenPosition(OrderPojo orderPojo) {
        long id = Long.parseLong(orderPojo.getClientOrderId().split("_")[0]);
        if (positions.containsKey(id)) {
            return;
        }
        BigDecimal baseAssetToNoDeciConv = new BigDecimal(10).pow(tradeConfigProperties.getQuoteAssetScale());

        Position position = Position.builder()
            .orderIdOpen(orderPojo.getOrderId())
            .openAtPrice(orderPojo.getPrice())
            .closeAtPrice(orderPojo.getPrice().add(new BigDecimal(tradeConfigProperties.getGapSizePoints()).setScale(tradeConfigProperties.getQuoteAssetScale(), RoundingMode.HALF_UP).divide(baseAssetToNoDeciConv, RoundingMode.HALF_UP)))
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
