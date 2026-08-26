package br.com.topsdojob.v3.platform.health;

import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final BackendReadinessService readinessService;

    public HealthController(BackendReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping
    public HealthResponse health(HttpServletRequest request) {
        return livenessResponse(request);
    }

    @GetMapping("/readiness")
    public ResponseEntity<HealthResponse> readiness(HttpServletRequest request) {
        BackendReadinessService.ReadinessResult result = readinessService.check();
        HttpStatus status = result.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(new HealthResponse(
                result.ready() ? "UP" : "DOWN",
                "topsdojob-v3-backend",
                RequestIdContext.current(request),
                result.components()));
    }

    @GetMapping("/liveness")
    public HealthResponse liveness(HttpServletRequest request) {
        return livenessResponse(request);
    }

    private HealthResponse livenessResponse(HttpServletRequest request) {
        return new HealthResponse(
                "UP",
                "topsdojob-v3-backend",
                RequestIdContext.current(request),
                Map.of("application", "UP"));
    }

    public record HealthResponse(
            String status,
            String app,
            String requestId,
            Map<String, String> components) {
    }
}
