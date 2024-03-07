package at.pepe.trader.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class OrderPojo {
    private Long orderId;
    private String clientOrderId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String symbol;
    private String action; // BUY or SELL
    private String type; // LIMIT
    private BigDecimal quantity; // Amount of base currency (left part of symbol) to acquire/sell.
    private BigDecimal executedQty;
    private BigDecimal price; // Price per base currency unit in quote currency (right part of symbol).
    private String orderStatus; // NEW, CANCELED, TRADE, EXPIRED, TRADE_PREVENTION
    private BigDecimal commissionAmount;
}
