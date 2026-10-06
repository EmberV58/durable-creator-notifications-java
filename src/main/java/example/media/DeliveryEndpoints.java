package example.media;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class DeliveryEndpoints {
    private final CreatorDelivery delivery;
    public DeliveryEndpoints(CreatorDelivery delivery) { this.delivery = delivery; }

    @PostMapping("/assets/completed")
    public CreatorDelivery.Notice completed(@RequestBody CreatorDelivery.AssetJob job) { return delivery.enqueue(job); }

    @PostMapping("/deliveries/drain")
    public Map<String, Integer> drain() { return Map.of("delivered", delivery.deliver()); }

    @ExceptionHandler(InfraiClient.ApiError.class)
    public ResponseEntity<Map<String, String>> rejected(InfraiClient.ApiError error) {
        int status = error.status >= 500 ? 502 : error.status;
        return ResponseEntity.status(status).body(Map.of("code", error.code, "message", error.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", error.getMessage()));
    }
}
