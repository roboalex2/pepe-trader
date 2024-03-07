package at.pepe.trader.service.candle;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.mapper.CandlestickMapper;
import com.binance.connector.client.WebSocketStreamClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarSeriesHolderService {

    private final CandlestickMapper candlestickMapper;
    private final TradeConfigProperties tradeConfigProperties;
    private final CandleDataAccessService candleDataAccessService;


    private BarSeries minuteSeries;
    private BarSeries secondSeries;

    @EventListener(ApplicationReadyEvent.class)
    private void initialSetup() {
        minuteSeries = new BaseBarSeries("1m");
        secondSeries = new BaseBarSeries("1s");
        requestCandles();
    }

    public synchronized void updateBarSeries(BaseBar baseBar) {
        if (secondSeries == null ||
                minuteSeries == null ||
                minuteSeries.getEndIndex() == -1 ||
                secondSeries.getEndIndex() == -1
        ) {
            return;
        }

        secondSeries.addBar(baseBar);
        if (!minuteSeries.getLastBar().getEndTime().isBefore(baseBar.getEndTime())) {
            minuteSeries.getLastBar().addPrice(baseBar.getClosePrice());
        } else {
            BaseBar minuteKline = BaseBar.builder()
                    .endTime(Instant.ofEpochSecond((baseBar.getEndTime().toEpochSecond() / 60) * 60 + 60).atZone(ZoneOffset.UTC))
                    .volume(baseBar.getVolume())
                    .closePrice(baseBar.getClosePrice())
                    .openPrice(baseBar.getOpenPrice())
                    .highPrice(baseBar.getHighPrice())
                    .lowPrice(baseBar.getLowPrice())
                    .timePeriod(minuteSeries.getLastBar().getTimePeriod())
                    .build();
            minuteSeries.addBar(minuteKline);
        }
    }

    private void requestCandles() {
        minuteSeries.setMaximumBarCount(2000);
        secondSeries.setMaximumBarCount(2000);

        List<BaseBar> minuteCandles = candlestickMapper.map(
                candleDataAccessService.getCandlesFromBinance(
                        tradeConfigProperties.getSymbol(),
                        "1m",
                        Instant.now().atOffset(ZoneOffset.UTC)
                )
        );
        minuteCandles.forEach(minuteSeries::addBar);

        List<BaseBar> secondCandles = candlestickMapper.map(
                candleDataAccessService.getCandlesFromBinance(
                        tradeConfigProperties.getSymbol(),
                        "1s",
                        Instant.now().atOffset(ZoneOffset.UTC)
                )
        );
        secondCandles.forEach(secondSeries::addBar);
    }

    public BarSeries getMinuteSeries() {
        return minuteSeries;
    }

    public BarSeries getSecondSeries() {
        return minuteSeries;
    }
}
