package example.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiClient(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                        @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.key = key;
    }

    public JsonNode post(String path, Object body, String requestId) {
        try {
            String payload = json.writeValueAsString(body);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", requestId)
                        .timeout(Duration.ofSeconds(15))
                        .method("POST", HttpRequest.BodyPublishers.ofString(payload)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    long seconds = response.headers().firstValue("Retry-After")
                            .flatMap(value -> {
                                try { return java.util.Optional.of(Long.parseLong(value)); }
                                catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
                            }).orElse(1L << attempt);
                    Thread.sleep(Math.max(1, Math.min(seconds, 30)) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new ApiError(response.statusCode(), error.path("code").asText("API_ERROR"), error.toString());
                }
                if (response.statusCode() >= 500) throw new ApiError(502, "UPSTREAM_ERROR", "Upstream response failed");
                return envelope.path("data");
            }
            throw new ApiError(429, "RATE_LIMITED", "Retry budget exhausted");
        } catch (IOException e) {
            throw new ApiError(502, "TRANSPORT_ERROR", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiError(503, "INTERRUPTED", e.getMessage());
        }
    }

    public static class ApiError extends RuntimeException {
        public final int status;
        public final String code;
        public ApiError(int status, String code, String message) {
            super(message); this.status = status; this.code = code;
        }
    }
}
