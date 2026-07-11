package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusArquivoMidia;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MidiaPublicaUrlServiceTest {

    @Test
    void naoGeraUrlPublicaAPartirDeBucketChaveProviderOuHash() {
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        ArquivoMidiaEntity arquivo = entity(ArquivoMidiaEntity.class);
        set(arquivo, "storageProvider", "s3-local-privado");
        set(arquivo, "bucket", "bucket-privado");
        set(arquivo, "chaveObjeto", "midia/privada/foto.jpg");
        set(arquivo, "sha256", "a".repeat(64));
        set(arquivo, "etag", "etag-privado");

        MidiaPublicaUrlService.ResultadoUrlPublica resultado =
                new MidiaPublicaUrlService().resolver(vinculo, arquivo);

        assertThat(resultado.urlPublica()).isNull();
        assertThat(resultado.pendenciaMidia()).isEqualTo(MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
        assertThat(resultado.toString())
                .doesNotContain("s3-local-privado")
                .doesNotContain("bucket-privado")
                .doesNotContain("midia/privada")
                .doesNotContain("aaaaaaaa")
                .doesNotContain("etag-privado");
    }

    @Test
    void resolveSomenteAssetEstaticoSeguroDoFixtureEmHomologacao() {
        ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "fixture/stories/foto-a.webp",
                "image/webp",
                StatusArquivoMidia.VALIDADO,
                OffsetDateTime.now(ZoneOffset.UTC));

        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.LIVRE);
        MidiaPublicaUrlService.ResultadoUrlPublica resultado = new MidiaPublicaUrlService(
                "homologacao", "https://v3.esle.cloud").resolver(vinculo, arquivo);

        assertThat(resultado.urlPublica()).isEqualTo("https://v3.esle.cloud/demo-safe-public.svg");
        assertThat(resultado.pendenciaMidia()).isNull();
        assertThat(resultado.toString())
                .doesNotContain("fixture/stories")
                .doesNotContain("topsv3-hml-fixture")
                .doesNotContain("LOCAL_MOCK");
    }

    @Test
    void recusaAssetDoFixtureForaDeHomologacao() {
        ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "fixture/stories/foto-a.webp",
                "image/webp",
                StatusArquivoMidia.VALIDADO,
                OffsetDateTime.now(ZoneOffset.UTC));

        var resultado = new MidiaPublicaUrlService(
                "producao", "https://topsdojob.com").resolver(entity(AnuncioMidiaEntity.class), arquivo);

        assertThat(resultado.urlPublica()).isNull();
        assertThat(resultado.pendenciaMidia()).isEqualTo(MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
    }

    @Test
    void nuncaResolveUrlParaMidiaRestritaMesmoEmHomologacao() {
        ArquivoMidiaEntity arquivo = ArquivoMidiaEntity.criarFixtureHomologacao(
                UUID.randomUUID(),
                "fixture/stories/restrita-a.webp",
                "image/webp",
                StatusArquivoMidia.VALIDADO,
                OffsetDateTime.now(ZoneOffset.UTC));
        AnuncioMidiaEntity vinculo = entity(AnuncioMidiaEntity.class);
        set(vinculo, "visibilidadeMidia", VisibilidadeMidia.RESTRITA_18);

        var resultado = new MidiaPublicaUrlService(
                "homologacao", "https://v3.esle.cloud").resolver(vinculo, arquivo);

        assertThat(resultado.urlPublica()).isNull();
        assertThat(resultado.pendenciaMidia()).isEqualTo(MidiaPublicaUrlService.PENDENTE_URL_PUBLICA_MIDIA_CDN);
        assertThat(resultado.toString()).doesNotContain("restrita-a.webp");
    }
}
