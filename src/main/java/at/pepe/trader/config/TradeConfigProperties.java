package at.pepe.trader.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "trade")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TradeConfigProperties {
    private String symbol;
    private String baseAsset;
    private int baseAssetScale;
    private String quoteAsset;
    private int quoteAssetScale;
    private BigDecimal quoteAssetQuantityPerTrade;
    private int gapSizePoints;
    private BigDecimal upperBounds;
    private BigDecimal lowerBounds;
    private String discordWebhook;
}
