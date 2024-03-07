package at.pepe.trader.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Position implements Serializable {
    private static final long serialVersionUID = 8038383777467488147L;

    private long id;
    private PositionStatus status;

    private Long orderIdOpen;
    private BigDecimal openAtPrice;
    private BigDecimal quantityOpen;

    private Long orderIdClose;
    private BigDecimal closeAtPrice;
    private BigDecimal quantityClose;

    private OffsetDateTime createdAt;
}
