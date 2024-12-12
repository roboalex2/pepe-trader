package at.pepe.trader.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class StatisticResult {

    private String timeFrame;
    private int positionsStillOpen;
    private int positionsClosed;
    private int positionsCancelled;
    private int positionsOpened;
    private Duration averageTimeToClose;

    private BigDecimal profitMade;
    private double volumenTraded;

    @Override
    public String toString() {
        return "PositionsClosed: " + positionsClosed +
                "\nPositionsStillOpen: " + positionsStillOpen +
                "\nPositionsCancelled: " + positionsCancelled +
                "\nPositionsOpened: " + positionsOpened +
                "\nAverageTimeToClose: " + averageTimeToClose +
                "\nTotalProfit: **" + new DecimalFormat("#,##0.00").format(profitMade) + " $**" +
                "\nVolumenTraded: " + String.format("%.2f", volumenTraded) + " $ ";
    }
}
