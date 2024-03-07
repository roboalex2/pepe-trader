package at.pepe.trader.config.binance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "binance")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BinanceConfigProperties {
    private String apikey;
    private String ed25519SecretPath;

    private String baseUrl;
    private String baseWebsocket;
    private String baseWebsocketApi;
}
