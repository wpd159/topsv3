package br.com.topsdojob.v3.platform.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HealthControllerPublicContractTest {

    @Test
    void healthPublicoNaoExpoeAmbienteOuEfiMock() throws Exception {
        String controller = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "platform",
                "health",
                "HealthController.java"));

        assertThat(controller)
                .doesNotContain("appEnv")
                .doesNotContain("environment")
                .doesNotContain("efiPixMockMode");
        assertThat(controller).contains(
                "String status",
                "String app",
                "String requestId",
                "Map<String, String> components");
        assertThat(controller)
                .doesNotContain("jdbc:")
                .doesNotContain("postgres:")
                .doesNotContain("R2")
                .doesNotContain("Efi");
    }
}
