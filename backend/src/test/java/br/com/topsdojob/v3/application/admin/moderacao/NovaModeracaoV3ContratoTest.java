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
                .contains("/api/admin/anuncios/filtros/localidades:")
                .contains("/api/admin/anuncios/{id}/aprovar:")
                .contains("/api/admin/anuncios/{id}/midias/decisoes:")
                .contains("/api/admin/midias/{id}/preview:")
                .contains("/api/admin/midias/{id}/reclassificar:")
                .contains("AdminModeracaoHistoricoItem:")
                .contains("AdminMidiaPreview:")
                .contains("AdminReclassificarMidiaRequest:")
                .contains("AdminDecidirFotosLoteRequest:")
                .contains("AdminDecidirFotosLoteResponse:")
                .contains("mesma transacao, a decisao administrativa em PUBLICADO/APROVADO")
                .contains("APROVAR leva o anuncio diretamente a PUBLICADO/APROVADO")
                .contains("notificacao idempotente MODERACAO_REPROVADA")
                .contains("Story retorna 409 e nao participa da moderacao")
                .contains("video aprovado permanece RESTRITA_18")
                .doesNotContain("/api/admin/moderacao-v2");
    }

    @Test
    void loteDeFotosExplicitaResultadoParcialEExclusaoCanonica() throws Exception {
        String source = Files.readString(OPENAPI, StandardCharsets.UTF_8);
        String batchContract = source.substring(
                source.indexOf("/api/admin/anuncios/{id}/midias/decisoes:"),
                source.indexOf("/api/admin/documentos:"));

        assertThat(batchContract)
                .contains("Prevalida todas as fotos")
                .contains("ObjectStorage canonico")
                .contains("AdminDecidirFotosLoteRequest")
                .contains("AdminDecidirFotosLoteResponse")
                .contains("\"503\"")
                .doesNotContain("DELETE");
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

    @Test
    void filaProtegidaExplicitaProprietarioIntegralSemCpf() throws Exception {
        String source = Files.readString(OPENAPI, StandardCharsets.UTF_8);
        String listContract = source.substring(
                source.indexOf("/api/admin/anuncios:"),
                source.indexOf("/api/admin/anuncios/{id}:"));
        String ownerSchema = source.substring(
                source.indexOf("    AdminAnuncianteResumo:"),
                source.indexOf("    AdminAnuncianteDetalhe:"));

        assertThat(listContract)
                .contains("protegida e no-store")
                .contains("nome civil, e-mail e WhatsApp integrais")
                .contains("/api/admin/anuncios/filtros/localidades:");
        assertThat(ownerSchema)
                .contains("nomeCivil:")
                .contains("email:")
                .contains("whatsapp:")
                .doesNotContain("emailMascarado")
                .doesNotContain("cpf:");
    }
}
