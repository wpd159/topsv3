package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MidiasPublicasSeoImportadorTest {

    private static final Path IMPORTACAO = Path.of(
            "..", "scripts", "local", "importacao");

    @Test
    void persisteBucketEChaveDaCopiaR2ConfiguradaSemUsarAOrigemOperacionalmente() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));
        String midia = sql.substring(sql.indexOf("-- Midia:"), sql.indexOf("-- Busca e SEO"));
        String persistencia = midia.substring(
                midia.indexOf("INSERT INTO arquivo_midia"),
                midia.indexOf("INSERT INTO stg_midia"));

        assertThat(sql)
                .contains(":'r2_public_media_bucket'")
                .contains(":'r2_public_media_prefix'")
                .contains(":'r2_private_media_bucket'")
                .contains(":'r2_private_media_prefix'")
                .contains("r2_preserved_public_bucket")
                .contains("r2_preserved_public_base_url")
                .contains("r2_preserved_public_prefix")
                .contains("r.object_key AS copied_object_key")
                .contains("MIDIA_URL_PUBLICA_INCOMPATIVEL")
                .doesNotContain("hml/midias-aprovadas/importacao/sha256/%")
                .doesNotContain("pub-567428d3703244d483815a05a1e0e0d9.r2.dev")
                .doesNotContain("= 115");
        assertThat(persistencia)
                .contains("c.r2_public_media_bucket")
                .contains("m.copied_object_key")
                .contains("md5('r2:arquivo-publico-destino:'")
                .doesNotContain("c.r2_preserved_public_bucket")
                .doesNotContain("m.source_object_key")
                .doesNotContain("arquivo-publico-preservado");
    }

    @Test
    void validadorExigeDestinoPublicoConfiguradoParaTodaMidiaLivre() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("validar-dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("MIDIA_PUBLICA_DESTINO_INVALIDA")
                .contains("ar.bucket <> c.r2_public_media_bucket")
                .contains("ar.chave_objeto NOT LIKE c.r2_public_media_prefix")
                .contains("am.visibilidade_midia = 'LIVRE'")
                .contains("am.tipo = 'FOTO'")
                .doesNotContain("MIDIA_PUBLICA_ORIGEM_INVALIDA")
                .doesNotContain("= 115");
    }

    @Test
    void falhaFechadoParaParametroAusenteOuManifestoDeOutroAmbiente() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("\\if :{?r2_public_media_bucket}")
                .contains("\\if :{?r2_public_media_prefix}")
                .contains("\\if :{?r2_private_media_bucket}")
                .contains("\\if :{?r2_private_media_prefix}")
                .contains("\\if :{?r2_document_bucket}")
                .contains("\\if :{?r2_document_prefix}")
                .contains("manifesto R2 publico nao corresponde ao prefixo e objetos do destino configurado")
                .contains("r.object_key NOT LIKE c.r2_public_media_prefix || 'importacao/sha256/%'")
                .contains("storageDestinationFingerprint");
    }

    @Test
    void evidenciaSeoDoImportadorPreservaBloqueioCanonicoDeConteudoGenerico() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("n.titulo_normalizado NOT IN")
                .contains("'acompanhante 1', 'acompanhante 2'")
                .contains("replace(n.slug_normalizado, ' ', '-') NOT IN")
                .contains("n.titulo_normalizado !~ '^(acompanhante|anuncio|perfil|teste)\\s*[0-9]*$'");
    }
}
