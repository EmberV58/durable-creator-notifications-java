package example.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatorDeliveryTest {
    static class RecordingClient extends InfraiClient {
        int calls;
        String queuedId;
        RecordingClient() { super(new ObjectMapper(), "http://localhost", "test-key"); }
        @Override public JsonNode post(String path, Object body, String requestId) {
            calls++;
            if (path.equals("/v1/queue/publish")) queuedId = requestId;
            return new ObjectMapper().createObjectNode();
        }
    }

    @Test void onlyProcessedJobsEnterTheQueue() {
        RecordingClient api = new RecordingClient();
        CreatorDelivery delivery = new CreatorDelivery(api);
        assertThrows(IllegalArgumentException.class, () -> delivery.enqueue(
                new CreatorDelivery.AssetJob("asset-7", "creator-3", "Pilot", "ingesting")));
        assertEquals(0, api.calls);
        CreatorDelivery.Notice notice = delivery.enqueue(
                new CreatorDelivery.AssetJob("asset-7", "creator-3", "Pilot", "processed"));
        assertEquals("asset-7", notice.assetId());
        assertEquals(2, api.calls);
        assertEquals("asset:asset-7:processed", api.queuedId);
    }
}
