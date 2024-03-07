package at.pepe.trader.service.binance;

import at.pepe.trader.mapper.OrderMapper;
import at.pepe.trader.model.OrderPojo;
import com.binance.connector.client.SpotClient;
import com.binance.connector.client.WebSocketStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.json.JSONObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataMonitorService {
    private final OrderMapper orderMapper;
    private final OrderHolderService orderHolderService;
    private final SpotClient spotClient;
    private final WebSocketStreamClient webSocketStreamClient;
    private final BalanceHolderService balanceHolderService;

    private String listenKey;
    private int websocketId;

    @EventListener(ApplicationReadyEvent.class)
    public void openUserDataStream() {
        webSocketStreamClient.closeConnection(websocketId);
        listenKey = new JSONObject(spotClient.createUserData().createListenKey()).getString("listenKey");
        websocketId = webSocketStreamClient.listenUserStream(
                listenKey,
                message -> {},
                this::userDataUpdateEvent,
                (i, m)-> {},
                this::websocketClosureEvent,
                this::websocketFailureEvent
        );
    }

    private void userDataUpdateEvent(String message) {
        JSONObject jsonObject = new JSONObject(message);
        switch (jsonObject.getString("e")) {
            case "outboundAccountPosition":
                balanceHolderService.updateAssets(jsonObject);
                break;
            case "executionReport":
                OrderPojo order = orderMapper.mapFromStream(jsonObject);
                orderHolderService.updateOrderStatus(order);
                break;
        }
    }


    @Scheduled(cron = "1 */20 * * * *")
    private void sendKeepAlive() {
        spotClient.createUserData().extendListenKey(Map.of("listenKey", listenKey));
    }

    private void invalidateListenKey() {
        try {
            spotClient.createUserData().closeListenKey(Map.of("listenKey", listenKey));
        } catch (RuntimeException exception) {
            log.error("Failed to delete userData listenKey.", exception);
        }
    }

    private void websocketFailureEvent(Throwable throwable, Response response) {
        log.warn(response.message(), throwable);
        invalidateListenKey();
        openUserDataStream();
    }

    private void websocketClosureEvent(int i, String message) {
        log.warn(message);
        invalidateListenKey();
        openUserDataStream();
    }
}
