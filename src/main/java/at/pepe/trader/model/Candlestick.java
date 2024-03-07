package at.pepe.trader.model;


import lombok.*;

import java.time.Duration;
import java.time.OffsetDateTime;

@Data
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Candlestick {

    private String symbol;
    private Duration interval;
    private OffsetDateTime openTime;
    private OffsetDateTime closeTime;

    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;


    public static Candlestick combiner(Candlestick a, Candlestick b) {
        return Candlestick.builder()
                .openTime(a.getOpenTime().isBefore(b.getOpenTime()) ? a.getOpenTime() : b.getOpenTime())
                .closeTime(a.getCloseTime().isAfter(b.getCloseTime()) ? a.getCloseTime() : b.getCloseTime())
                .interval(a.getInterval().plus(b.getInterval()))
                .open(a.getOpenTime().isBefore(b.getOpenTime()) ? a.getOpen() : b.getOpen())
                .close(a.getCloseTime().isAfter(b.getCloseTime()) ? a.getClose() : b.getClose())
                .high(Math.max(a.getHigh(), b.getHigh()))
                .low(Math.min(a.getLow(), b.getLow()))
                .symbol(a.getSymbol())
                .volume(a.getVolume() + b.getVolume())
                .build();
    }
}