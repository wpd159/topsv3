package br.com.topsdojob.v3.web.admin.desempenho;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminDesempenhoControllerContractTest {

    @Test
    void desempenhoAdminPossuiSomenteGetsReadOnly() throws Exception {
        String controller = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "web",
                "admin",
                "desempenho",
                "AdminDesempenhoController.java"));

        assertThat(controller)
                .contains("@GetMapping")
                .contains("ANUNCIO_LER")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("comprar")
                .doesNotContain("pagar")
                .doesNotContain("impulsionar")
                .doesNotContain("tracking")
                .doesNotContain("pixel");
    }
}
