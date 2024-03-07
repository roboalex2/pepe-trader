package at.pepe.trader.service.candle;

import at.pepe.trader.model.Candlestick;
import com.binance.connector.client.SpotClient;
import com.binance.connector.client.exceptions.BinanceClientException;
import com.binance.connector.client.exceptions.BinanceConnectorException;
import com.binance.connector.client.exceptions.BinanceServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleDataAccessService {

    private final ObjectMapper objectMapper;
    private final SpotClient spotClient;

    // Currently only supports 1s and 1m candles
    public List<Candlestick> getCandlesFromBinance(String symbol, String intervall, OffsetDateTime endTime) {
        String klines;
        try {
            klines = spotClient.createMarket().klines(Map.of(
                    "symbol", symbol,
                    "interval", intervall,
                    "endTime", endTime.toInstant().toEpochMilli(),
                    "limit", 1000,
                    "timeZone", 0
            ));
        } catch (BinanceClientException | BinanceConnectorException | BinanceServerException exception) {
            log.warn(String.valueOf(exception));
            return List.of();
        }

        ArrayList<ArrayList<Object>> rawCandleresult = null;
        try {
            rawCandleresult = objectMapper.readValue(klines, ArrayList.class);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn(String.valueOf(e));
            return List.of();
        }
        return rawCandleresult.stream()
                .map(data -> Candlestick.builder()
                        .openTime(Instant.ofEpochMilli((Long) data.get(0)).atOffset(ZoneOffset.UTC))
                        .open(new BigDecimal((String) data.get(1)).doubleValue())
                        .high(new BigDecimal((String) data.get(2)).doubleValue())
                        .low(new BigDecimal((String) data.get(3)).doubleValue())
                        .close(new BigDecimal((String) data.get(4)).doubleValue())
                        .volume(new BigDecimal((String) data.get(5)).doubleValue())
                        .closeTime(Instant.ofEpochMilli((Long) data.get(6)).atOffset(ZoneOffset.UTC))
                        .symbol(symbol)
                        .interval("1m".equals(intervall) ? Duration.ofMinutes(1) : Duration.ofSeconds(1))
                        .build())
                .collect(Collectors.toList());
    }
}
