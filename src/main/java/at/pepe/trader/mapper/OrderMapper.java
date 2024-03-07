package at.pepe.trader.mapper;

import at.pepe.trader.model.OrderPojo;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

@Component
public class OrderMapper {

    public OrderPojo mapFromStream(JSONObject jsonObject) {
        return OrderPojo.builder()
                .orderId(jsonObject.optLongObject("i"))
                .clientOrderId(Strings.isNotBlank(jsonObject.getString("C")) ? jsonObject.getString("C") : jsonObject.getString("c"))
                .action(jsonObject.getString("S"))
                .symbol(jsonObject.getString("s"))
                .orderStatus(jsonObject.getString("X"))
                .createdAt(Instant.ofEpochMilli(jsonObject.getLong("O")).atOffset(ZoneOffset.UTC))
                .updatedAt(Instant.ofEpochMilli(jsonObject.getLong("E")).atOffset(ZoneOffset.UTC))
                .type(jsonObject.getString("o"))
                .price(jsonObject.getBigDecimal("p"))
                .quantity(jsonObject.getBigDecimal("q"))
                .executedQty(jsonObject.getBigDecimal("z"))
                .commissionAmount(jsonObject.getBigDecimal("n"))
                .build();

    }

    public OrderPojo mapFromSpot(JSONObject jsonObject) {
        return OrderPojo.builder()
                .orderId(jsonObject.optLongObject("orderId"))
                .clientOrderId(jsonObject.getString("clientOrderId"))
                .action(jsonObject.getString("side"))
                .symbol(jsonObject.getString("symbol"))
                .orderStatus(jsonObject.getString("status"))
                .createdAt(Instant.ofEpochMilli(jsonObject.getLong("time")).atOffset(ZoneOffset.UTC))
                .updatedAt(Instant.ofEpochMilli(jsonObject.getLong("updateTime")).atOffset(ZoneOffset.UTC))
                .type(jsonObject.getString("type"))
                .price(jsonObject.getBigDecimal("price"))
                .quantity(jsonObject.getBigDecimal("origQty"))
                .executedQty(jsonObject.getBigDecimal("executedQty"))
                .build();

    }
}
