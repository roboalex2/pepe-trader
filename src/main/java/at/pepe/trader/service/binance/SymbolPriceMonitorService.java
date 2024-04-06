package at.pepe.trader.service.binance;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.mapper.CandlestickMapper;
import at.pepe.trader.service.TradingService;
import at.pepe.trader.service.candle.BarSeriesHolderService;
import com.binance.connector.client.WebSocketStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.json.JSONObject;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBar;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolPriceMonitorService {

    private final CandlestickMapper candlestickMapper;
    private final TradeConfigProperties tradeConfigProperties;
    private final WebSocketStreamClient webSocketStreamClient;
    private final BarSeriesHolderService barSeriesHolderService;
    private final TradingService tradingService;

    private int streamId;

    @EventListener(ApplicationReadyEvent.class)
    private void openWebsocketStream() {
        webSocketStreamClient.closeConnection(streamId);
        streamId = webSocketStreamClient.klineStream(
                tradeConfigProperties.getSymbol().toLowerCase(),
                "1s",
                msg -> {},
                this::priceUpdateEvent,
                (i, m)-> {},
                this::websocketClosureEvent,
                this::websocketFailureEvent
        );
    }

    private void priceUpdateEvent(String message) {
        JSONObject jsonKline = new JSONObject(message).getJSONObject("k");
        BaseBar secondKline = candlestickMapper.map(jsonKline);
        try {
            barSeriesHolderService.updateBarSeries(secondKline);
            tradingService.performTrade();
        } catch (RuntimeException exception) {
            log.warn("Failure on priceUpdateEvent: ", exception);
        }
    }

    private void websocketClosureEvent(int i, String message) {
        log.warn(message);
        openWebsocketStream();
    }

    private void websocketFailureEvent(Throwable throwable, Response response) {
        log.warn(Optional.ofNullable(response).map(Response::message).orElse("Websocket Failure for price update: ") , throwable);
        openWebsocketStream();
    }
}
