package at.pepe.trader.service.discord;

import at.pepe.trader.config.TradeConfigProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.logging.log4j.util.Strings;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordEmbedPublishingService {

    private final ObjectMapper objectMapper;
    private final TradeConfigProperties tradeConfigProperties;

    @Async
    public void sendEmbed(String title, String description, String color) {

        if (Strings.isBlank(tradeConfigProperties.getDiscordWebhook())) {
            return;
        }

        try {
            // JSON-Payload erstellen
            ObjectNode embed = objectMapper.createObjectNode();
            embed.put("title", title);
            embed.put("description", description);
            embed.put("color", color);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.putArray("embeds").add(embed);

            // JSON-Payload in String umwandeln
            String jsonPayload = objectMapper.writeValueAsString(payload);

            // Anfrage senden
            RequestBody body = RequestBody.create(
                jsonPayload, MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                .url(tradeConfigProperties.getDiscordWebhook())
                .post(body)
                .build();

            try (Response response = new OkHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Invalid Response: " + response);
                }
            }
        } catch (Exception exception) {
            log.warn("Failed to send discord embed.", exception);
        }
    }
}
