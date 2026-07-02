package br.com.topsdojob.v3.application.publico.service;

import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.entity;
import static br.com.topsdojob.v3.application.publico.PublicApiReflectionTestSupport.set;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
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
}
