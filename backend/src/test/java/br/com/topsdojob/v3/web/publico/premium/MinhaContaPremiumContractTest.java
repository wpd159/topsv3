package br.com.topsdojob.v3.web.publico.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MinhaContaPremiumContractTest {

    @Test
    void compraPublicaUsaUmaTransacaoEChaveIdempotente() throws Exception {
        String service = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3", "application", "publico", "premium",
                "MinhaContaPremiumService.java"));
        String controller = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3", "web", "publico", "anunciante",
                "MinhaContaPremiumController.java"));

        assertThat(service)
                .contains("@Transactional\n    public MinhaCompraPremiumResultadoDto comprar")
                .contains("premium-compra:")
                .contains("findByIdempotencyKey")
                .contains("saldo de creditos insuficiente")
                .contains("PREMIUM_COMPRA_CREDITOS")
                .doesNotContain("saldoCreditoRepository")
                .doesNotContain("setSaldo");
        assertThat(controller)
                .contains("@PostMapping(\"/compras\")")
                .contains("@RequestHeader(\"Idempotency-Key\")")
                .contains("RequestIdContext.current(request)");
    }
}
