package at.pepe.trader.service.binance;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.mapper.OrderMapper;
import at.pepe.trader.model.OrderPojo;
import at.pepe.trader.service.position.PositionService;
import com.binance.connector.client.SpotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderHolderService {
    private final OrderMapper orderMapper;
    private final PositionService positionService;
    private final TradeConfigProperties tradeConfigProperties;
    private final SpotClient spotClient;
    private Map<String, OrderPojo> openOrders = new ConcurrentHashMap<>();


    public void updateOrderStatus(OrderPojo order) {
        if (tradeConfigProperties.getSymbol().equals(order.getSymbol())) {
            openOrders.put(order.getClientOrderId(), order);
            positionService.onOrderUpdateEvent(order);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 */20 * * * *") // All 20min we make sure that our orders are not out of sync
    private void requestAllOpenOrders() {
        String openOrderResponse = spotClient.createTrade().getOpenOrders(new HashMap<>(Map.of(
                "timestamp", System.currentTimeMillis(),
                "symbol", tradeConfigProperties.getSymbol()
        )));

        JSONArray orders = new JSONArray(openOrderResponse);
        for (int i = 0; i < orders.length(); i++) {
            OrderPojo order = orderMapper.mapFromSpot(orders.getJSONObject(i));
            updateOrderStatus(order);
        }
    }
}
