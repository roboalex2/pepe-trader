package at.pepe.trader.config.binance;

import com.binance.connector.client.SpotClient;
import com.binance.connector.client.WebSocketApiClient;
import com.binance.connector.client.WebSocketStreamClient;
import com.binance.connector.client.impl.SpotClientImpl;
import com.binance.connector.client.impl.WebSocketApiClientImpl;
import com.binance.connector.client.impl.WebSocketStreamClientImpl;
import com.binance.connector.client.impl.spot.Market;
import com.binance.connector.client.utils.signaturegenerator.Ed25519SignatureGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class BinanceConfig {

    private final BinanceConfigProperties binanceConfigProperties;

    @Bean
    public SpotClient getBinanceSpotClient() throws IOException {
        Ed25519SignatureGenerator signGenerator =  new Ed25519SignatureGenerator(binanceConfigProperties.getEd25519SecretPath());
        return new SpotClientImpl(binanceConfigProperties.getApikey(), signGenerator, binanceConfigProperties.getBaseUrl());
    }

    @Bean
    public WebSocketStreamClient getBinanceWebSocketStreamClient() {
        return new WebSocketStreamClientImpl(binanceConfigProperties.getBaseWebsocket());
    }

    @Bean
    public WebSocketApiClient getBinanceWebSocketApiClient() throws IOException {
        Ed25519SignatureGenerator signGenerator =  new Ed25519SignatureGenerator(binanceConfigProperties.getEd25519SecretPath());
        return new WebSocketApiClientImpl(binanceConfigProperties.getApikey(), signGenerator, binanceConfigProperties.getBaseWebsocketApi());
    }
}
