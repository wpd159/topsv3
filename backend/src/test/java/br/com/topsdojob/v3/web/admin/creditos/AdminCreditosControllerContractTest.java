package br.com.topsdojob.v3.web.admin.creditos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminCreditosControllerContractTest {

    @Test
    void creditosAdminExigeRbacEIdempotenciaNasOperacoes() throws Exception {
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
                "creditos",
                "AdminCreditosController.java"));

        assertThat(controller)
                .contains("@GetMapping")
                .contains("hasRole('ADMIN')")
                .contains("FINANCEIRO_LER")
                .contains("@PostMapping(\"/usuarios/{id}/ajustes\")")
                .contains("@PostMapping(\"/movimentos/{id}/estornos\")")
                .contains("@PutMapping(\"/pacotes/{id}\")")
                .contains("FINANCEIRO_GERENCIAR")
                .contains("@RequestHeader(\"Idempotency-Key\")")
                .contains("RequestIdContext.current(request)")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("comprar")
                .doesNotContain("pagar")
                .doesNotContain("conciliar");
    }
}
