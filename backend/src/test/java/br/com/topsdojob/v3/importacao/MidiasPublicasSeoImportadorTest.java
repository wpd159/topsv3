package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MidiasPublicasSeoImportadorTest {

    private static final Path IMPORTACAO = Path.of(
            "..", "scripts", "local", "importacao");

    @Test
    void preservaBucketEChavePublicosSemTrocarACopiaR2DoDryRun() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("r2_preserved_public_bucket")
                .contains("r2_preserved_public_base_url")
                .contains("r2_preserved_public_prefix")
                .contains("r.object_key AS copied_object_key")
                .contains("m.source_object_key")
                .contains("md5('r2:arquivo-publico-preservado:' || m.source_object_key)")
                .contains("MIDIA_URL_PUBLICA_INCOMPATIVEL")
                .doesNotContain("pub-567428d3703244d483815a05a1e0e0d9.r2.dev")
                .doesNotContain("= 115");
    }

    @Test
    void validadorExigeOrigemPublicaRealParaTodaMidiaLivre() throws Exception {
        String sql = Files.readString(IMPORTACAO.resolve("validar-dryrun-producao-v3-saneado.sql"));

        assertThat(sql)
                .contains("MIDIA_PUBLICA_ORIGEM_INVALIDA")
                .contains("ar.bucket <> c.r2_preserved_public_bucket")
                .contains("ar.chave_objeto NOT LIKE c.r2_preserved_public_prefix")
                .contains("am.visibilidade_midia = 'LIVRE'")
                .contains("am.tipo = 'FOTO'")
                .doesNotContain("= 115");
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
