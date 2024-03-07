package at.pepe.trader.mapper;

import at.pepe.trader.model.Candlestick;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

import java.time.*;
import java.util.List;

@Component
public class CandlestickMapper {

    public List<BaseBar> map(List<Candlestick> candlesticks) {
        return candlesticks.stream().map(this::map).toList();
    }

    public BaseBar map(Candlestick candlestick) {
        return BaseBar.builder(DecimalNum::valueOf, Number.class)
                .timePeriod(candlestick.getInterval())
                .endTime(candlestick.getCloseTime().toInstant().atZone(ZoneOffset.UTC))
                .openPrice(candlestick.getOpen())
                .highPrice(candlestick.getHigh())
                .lowPrice(candlestick.getLow())
                .closePrice(candlestick.getClose())
                .volume(candlestick.getVolume())
                .build();
    }

    public BaseBar map(JSONObject jsonKline) {
        return BaseBar.builder(DecimalNum::valueOf, Number.class)
                .timePeriod("1m".equals(jsonKline.getString("i")) ? Duration.ofMinutes(1) : Duration.ofSeconds(1))
                .endTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(jsonKline.getLong("T")), ZoneOffset.UTC))
                .openPrice(jsonKline.getBigDecimal("o"))
                .closePrice(jsonKline.getBigDecimal("c"))
                .highPrice(jsonKline.getBigDecimal("h"))
                .lowPrice(jsonKline.getBigDecimal("l"))
                .volume(jsonKline.getBigDecimal("v"))
                .build();
    }
}
