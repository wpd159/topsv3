package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class HealthControllerTest {

    private final BackendReadinessService readinessService = mock(BackendReadinessService.class);
    private final HealthController controller = new HealthController(readinessService);

    @Test
    void livenessEHealthIndependemDasDependencias() {
        var request = new MockHttpServletRequest("GET", "/api/health/liveness");

        var health = controller.health(request);
        var liveness = controller.liveness(request);

        assertThat(health.status()).isEqualTo("UP");
        assertThat(health.components()).containsExactlyEntriesOf(Map.of("application", "UP"));
        assertThat(liveness.status()).isEqualTo("UP");
        assertThat(liveness.components()).containsExactlyEntriesOf(Map.of("application", "UP"));
    }

    @Test
    void readinessSaudavelRetorna200SemDetalhesInternos() {
        when(readinessService.check()).thenReturn(new BackendReadinessService.ReadinessResult(
                true,
                Map.of("application", "UP", "database", "UP", "migrations", "UP")));

        var response = controller.readiness(
                new MockHttpServletRequest("GET", "/api/health/readiness"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().components())
                .containsOnlyKeys("application", "database", "migrations")
                .doesNotContainValue("jdbc:postgresql://postgres:5432/topsv3");
    }

    @Test
    void readinessReprovadaRetorna503Sanitizado() {
        when(readinessService.check()).thenReturn(new BackendReadinessService.ReadinessResult(
                false,
                Map.of("application", "UP", "database", "DOWN", "migrations", "DOWN")));

        var response = controller.readiness(
                new MockHttpServletRequest("GET", "/api/health/readiness"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DOWN");
        assertThat(response.getBody().toString())
                .doesNotContain("postgres")
                .doesNotContain("exception")
                .doesNotContain("localhost");
    }
}
