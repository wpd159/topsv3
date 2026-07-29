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
                .contains("validarRetry")
                .contains("PremiumBeneficioCodigo.TODOS")
                .contains("saldo de creditos insuficiente")
                .contains("PREMIUM_COMPRA_CREDITOS")
                .doesNotContain("saldoCreditoRepository")
                .doesNotContain("setSaldo");
        assertThat(controller)
                .contains("@PostMapping(\"/compras\")")
                .contains("@RequestHeader(\"Idempotency-Key\")")
                .contains("RequestIdContext.current(request)");
    }

    @Test
    void contratoNaoFixaDuracoesNemPermiteStoriesNoLote() throws Exception {
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));
        String frontend = Files.readString(Path.of(
                "..", "frontend", "src", "features", "monetizacao-wizard", "api.ts"));
        String service = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3", "application", "publico", "premium",
                "MinhaContaPremiumService.java"));

        String schemaCompra = openApi.substring(
                openApi.indexOf("    MinhaCompraPremiumRequest:"),
                openApi.indexOf("    MinhaCompraPremiumResultado:"));
        assertThat(schemaCompra)
                .contains("duracaoDias: { type: integer, minimum: 1 }")
                .doesNotContain("duracaoDias: { type: integer, enum: [1, 7, 14, 30] }");
        assertThat(frontend)
                .doesNotContain("function isCatalogCode")
                .doesNotContain("'Idempotency-Key': idempotencyKey()");
        assertThat(service)
                .contains("PremiumBeneficioCodigo.TODOS.contains(codigo)")
                .contains("chave de idempotencia reutilizada com compra diferente");
    }
}
