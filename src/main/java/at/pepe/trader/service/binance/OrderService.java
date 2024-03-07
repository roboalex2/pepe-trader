package at.pepe.trader.service.binance;

import at.pepe.trader.config.TradeConfigProperties;
import com.binance.connector.client.WebSocketApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.json.JSONObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final WebSocketApiClient webSocketApiClient;
    private final TradeConfigProperties tradeConfigProperties;

    private JSONObject lastAction;
    public void createNewOrder(BigDecimal price, BigDecimal quantity, String action, long clientId) {
        String value = price.setScale(8, RoundingMode.HALF_UP).toPlainString().strip();
        webSocketApiClient.trade().newOrder(
                tradeConfigProperties.getSymbol(),
                action,
                "LIMIT",
                lastAction = new JSONObject(Map.of(
                        "quantity", quantity.toPlainString(),
                        "price", value,
                        "newClientOrderId", clientId + "_" + action,
                        "timeInForce", "GTC",
                        "timestamp", Instant.now().toEpochMilli() - 10
                ))
        );
    }

    public void cancelOrder(long orderId) {
        webSocketApiClient.trade().cancelOrder(
                tradeConfigProperties.getSymbol(),
                lastAction = new JSONObject(Map.of(
                        "orderId", orderId,
                        "timestamp", Instant.now().toEpochMilli() - 10
                ))
        );
    }


    @EventListener(ApplicationReadyEvent.class)
    public void openApiStream() {
        try {
            webSocketApiClient.close();
        } catch (Exception e){}

        webSocketApiClient.connect(
                message -> {
                },
                this::onApiResponseEvent,
                (i, m) -> {
                },
                this::websocketClosureEvent,
                this::websocketFailureEvent
        );
    }

    private void onApiResponseEvent(String message) {
        JSONObject jsonObject = new JSONObject(message);
        if (jsonObject.getInt("status") > 300) {
            log.info("Probable cause: {}", lastAction);
            log.info(message);
        }
        log.debug(message);
    }

    private void websocketFailureEvent(Throwable throwable, Response response) {
        log.warn(response.message(), throwable);
        openApiStream();
    }

    private void websocketClosureEvent(int i, String message) {
        log.warn(message);
        openApiStream();
    }
}
