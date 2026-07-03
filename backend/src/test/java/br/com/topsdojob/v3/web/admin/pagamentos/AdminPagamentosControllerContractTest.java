package br.com.topsdojob.v3.web.admin.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminPagamentosControllerContractTest {

    @Test
    void pagamentosAdminPossuiSomenteGetsReadOnly() throws Exception {
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
                "pagamentos",
                "AdminPagamentosController.java"));

        assertThat(controller)
                .contains("@GetMapping")
                .contains("hasRole('ADMIN')")
                .contains("FINANCEIRO_LER")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("comprar")
                .doesNotContain("pagar")
                .doesNotContain("estornar")
                .doesNotContain("ajustar")
                .doesNotContain("conciliar");
    }
}
