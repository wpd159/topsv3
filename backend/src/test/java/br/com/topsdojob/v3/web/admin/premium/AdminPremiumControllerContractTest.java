package br.com.topsdojob.v3.web.admin.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminPremiumControllerContractTest {

    @Test
    void premiumAdminProtegeCatalogoECancelamentoComRbac() throws Exception {
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
                "premium",
                "AdminPremiumController.java"));

        assertThat(controller)
                .contains("@GetMapping")
                .contains("@PreAuthorize")
                .doesNotContain("@PostMapping(\"/catalogo\")")
                .contains("@PutMapping(\"/catalogo/{id}\")")
                .contains("@PostMapping(\"/anuncios/{id}/ativacoes\")")
                .contains("@PostMapping(\"/anuncios/{id}/ativacoes/lote\")")
                .contains("@PostMapping(\"/ativacoes/{id}/cancelar\")")
                .contains("PREMIUM_GERENCIAR")
                .contains("@RequestHeader(\"Idempotency-Key\")")
                .contains("hasRole('ADMIN')")
                .contains("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("comprar")
                .doesNotContain("pagar");
    }
}
