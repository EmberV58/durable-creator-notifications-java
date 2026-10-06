package example.media;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class CreatorDelivery {
    private static final String QUEUE = "creator-notifications";
    private final InfraiClient api;
    public CreatorDelivery(InfraiClient api) { this.api = api; }

    public record AssetJob(String assetId, String creatorId, String title, String stage) {}
    public record Notice(String assetId, String creatorId, String title, String stage) {}

    public Notice completion(AssetJob job) {
        if (!"processed".equals(job.stage())) throw new IllegalArgumentException("Job must be processed");
        return new Notice(job.assetId(), job.creatorId(), job.title(), job.stage());
    }

    public Notice enqueue(AssetJob job) {
        Notice notice = completion(job);
        api.post("/v1/realtime/channel/create", Map.of("channel", "creator-" + notice.creatorId()),
                "channel:" + notice.creatorId());
        api.post("/v1/queue/publish", Map.of("queue", QUEUE, "payload", notice),
                "asset:" + notice.assetId() + ":processed");
        return notice;
    }

    public int deliver() {
        JsonNode batch = api.post("/v1/queue/consume", Map.of("queue", QUEUE,
                "max_messages", 10, "visibility_timeout", 30), "consume:" + java.util.UUID.randomUUID());
        JsonNode messages = batch.isArray() ? batch : batch.path("messages");
        int delivered = 0;
        if (messages.isArray()) for (JsonNode message : messages) {
            JsonNode payload = message.path("payload");
            if (payload.isTextual()) {
                try { payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload.asText()); }
                catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid notification payload", e); }
            }
            String creator = payload.path("creatorId").asText();
            String asset = payload.path("assetId").asText();
            if (creator.isBlank() || asset.isBlank()) throw new IllegalArgumentException("Notification identity missing");
            api.post("/v1/realtime/publish", Map.of("channel", "creator-" + creator,
                    "event", "asset.processed", "data", payload), "asset:" + asset + ":delivery:" + creator);
            delivered++;
        }
        return delivered;
    }
}
