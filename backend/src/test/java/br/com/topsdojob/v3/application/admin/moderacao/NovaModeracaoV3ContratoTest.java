package br.com.topsdojob.v3.application.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NovaModeracaoV3ContratoTest {

    private static final Path OPENAPI = Path.of(
            "..", "contracts", "openapi", "topsdojob-v3-local.yaml");

    @Test
    void openApiPublicaSomenteOsContratosCanonicosDaModeracaoV3() throws Exception {
        String source = Files.readString(OPENAPI, StandardCharsets.UTF_8);

        assertThat(source)
                .contains("/api/admin/anuncios/{id}/historico-moderacao:")
                .contains("/api/admin/midias/{id}/preview:")
                .contains("AdminModeracaoHistoricoItem:")
                .contains("AdminMidiaPreview:")
                .contains("Story retorna 409 e nao participa da moderacao")
                .contains("video aprovado permanece RESTRITA_18")
                .doesNotContain("/api/admin/moderacao-v2");
    }

    @Test
    void contratoNaoPrometeExclusaoFisicaNemUrlPrivadaPermanente() throws Exception {
        String source = Files.readString(OPENAPI, StandardCharsets.UTF_8);
        String decisionContract = source.substring(
                source.indexOf("/api/admin/midias/{id}/decidir:"),
                source.indexOf("/api/admin/moderacao/revisoes:"));

        assertThat(decisionContract)
                .contains("Nao apaga arquivo")
                .contains("nao retorna bucket, chave, hash ou URL privada")
                .doesNotContain("DELETE");
    }
}
