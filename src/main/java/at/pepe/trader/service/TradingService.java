package at.pepe.trader.service;

import at.pepe.trader.config.TradeConfigProperties;
import at.pepe.trader.service.binance.BalanceHolderService;
import at.pepe.trader.service.candle.BarSeriesHolderService;
import at.pepe.trader.service.position.PositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingService {
    private final TradeConfigProperties tradeConfigProperties;
    private final PositionService positionService;
    private final BarSeriesHolderService barSeriesHolderService;
    private final BalanceHolderService balanceHolderService;

    private BigDecimal lastActionPrice;

    @Async
    public void performTrade() {
        BarSeries minutes = barSeriesHolderService.getMinuteSeries();
        BigDecimal currentPrice = ((DecimalNum) minutes.getLastBar().getClosePrice()).getDelegate();

        BigDecimal quoteAssetToScale = new BigDecimal(10).pow(tradeConfigProperties.getQuoteAssetScale());
        BigDecimal deviation = (BigDecimal) stdDeviationIndicator(minutes).getValue(minutes.getEndIndex())
                .multipliedBy(DecimalNum.valueOf(quoteAssetToScale))
                .getDelegate();

        if (!currentPrice.equals(lastActionPrice) && deviation.doubleValue() <= tradeConfigProperties.getMaxStdDivergencePoints() &&
                upperIndicator(minutes).getValue(minutes.getEndIndex()).isGreaterThan(DecimalNum.valueOf(currentPrice)) &&
                lowerIndicator(minutes).getValue(minutes.getEndIndex()).isLessThan(DecimalNum.valueOf(currentPrice)) &&
                balanceHolderService.getAvailableQuoteAsset().doubleValue() >= tradeConfigProperties.getQuoteAssetQuantityPerTrade().doubleValue()
        ) {
            lastActionPrice = currentPrice;

            positionService.openPosition(
                    currentPrice.subtract(
                            new BigDecimal(tradeConfigProperties.getGapSizePoints())
                                    .divide(new BigDecimal(2), RoundingMode.UP)
                                    .setScale(tradeConfigProperties.getQuoteAssetScale(), RoundingMode.DOWN)
                                    .divide(quoteAssetToScale, RoundingMode.UNNECESSARY)
                    )
            );
        }
    }


    private BollingerBandsMiddleIndicator middleIndicator20(BarSeries series) {
        return new BollingerBandsMiddleIndicator(new EMAIndicator(new ClosePriceIndicator(series), 20));
    }

    private StandardDeviationIndicator stdDeviationIndicator(BarSeries series) {
        return new StandardDeviationIndicator(new ClosePriceIndicator(series), 20);
    }

    private BollingerBandsUpperIndicator upperIndicator(BarSeries series) {
        return new BollingerBandsUpperIndicator(middleIndicator20(series), stdDeviationIndicator(series), DecimalNum.valueOf(1.5));
    }

    private BollingerBandsLowerIndicator lowerIndicator(BarSeries series) {
        return new BollingerBandsLowerIndicator(middleIndicator20(series), stdDeviationIndicator(series), DecimalNum.valueOf(1.5));
    }
}
