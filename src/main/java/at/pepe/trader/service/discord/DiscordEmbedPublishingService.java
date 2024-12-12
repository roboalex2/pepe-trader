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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordEmbedPublishingService {

    private final ObjectMapper objectMapper;
    private final TradeConfigProperties tradeConfigProperties;

    @Async
    public void sendEmbed(String title, String description, String hexColor) {

        if (Strings.isBlank(tradeConfigProperties.getDiscordWebhook())) {
            return;
        }

        try {
            int color = Integer.parseInt(hexColor.replace("#", ""), 16);

            ObjectNode footer = objectMapper.createObjectNode();
            footer.put(
                    "text",
                    Instant.now().atZone(ZoneId.of("Europe/Vienna")).toOffsetDateTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))
            );

            // Create the embed object
            ObjectNode embed = objectMapper.createObjectNode();
            embed.put("id", 652627557);  // Example ID, replace if necessary
            embed.put("title", title);
            embed.put("description", description);
            embed.put("color", color);
            embed.putArray("fields");  // Empty array
            embed.set("footer", footer);

            // Create the main payload object
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("content", "");  // Empty content
            payload.put("tts", false);  // No text-to-speech
            payload.putArray("embeds").add(embed);
            payload.putArray("components");  // Empty components array
            payload.set("actions", objectMapper.createObjectNode());  // Empty actions object

            // Convert the payload to a JSON string
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
