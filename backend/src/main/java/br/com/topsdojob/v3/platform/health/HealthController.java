package br.com.topsdojob.v3.platform.health;

import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String appEnv;
    private final boolean efiPixMockMode;

    public HealthController(
            @Value("${app.env:local}") String appEnv,
            @Value("${efi.pix.mock-mode:true}") boolean efiPixMockMode) {
        this.appEnv = appEnv;
        this.efiPixMockMode = efiPixMockMode;
    }

    @GetMapping
    public HealthResponse health(HttpServletRequest request) {
        return response(request);
    }

    @GetMapping("/readiness")
    public HealthResponse readiness(HttpServletRequest request) {
        return response(request);
    }

    @GetMapping("/liveness")
    public HealthResponse liveness(HttpServletRequest request) {
        return response(request);
    }

    private HealthResponse response(HttpServletRequest request) {
        return new HealthResponse(
                "UP",
                "topsdojob-v3-backend",
                appEnv,
                efiPixMockMode,
                RequestIdContext.current(request));
    }

    public record HealthResponse(
            String status,
            String app,
            String environment,
            boolean efiPixMockMode,
            String requestId) {
    }
}
