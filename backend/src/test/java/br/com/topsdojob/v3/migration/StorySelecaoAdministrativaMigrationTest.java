package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StorySelecaoAdministrativaMigrationTest {

    @Test
    void v019CriaSingletonSemStoryCreditoOuCopiaDeMidia() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V019__selecao_administrativa_stories.sql"));

        assertThat(sql)
                .contains("CREATE TABLE story_selecao_administrativa")
                .contains("singleton_id smallint PRIMARY KEY")
                .contains("CHECK (singleton_id = 1)")
                .contains("anuncio_id uuid REFERENCES anuncio (id)")
                .contains("ativado_por uuid REFERENCES usuario (id)")
                .contains("criado_em timestamptz NOT NULL")
                .doesNotContain("story_anuncio_id")
                .doesNotContain("arquivo_midia_id")
                .doesNotContain("movimento_credito")
                .doesNotContain("beneficio_premium")
                .doesNotContain("INSERT INTO story_anuncio")
                .doesNotContain("UPDATE story_anuncio")
                .doesNotContain("INSERT INTO arquivo_midia");
    }

    @Test
    void v046PreservaLegadoERestringeConcorrenciaDeStoriesEFotosPendentes() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V046__stories_cumulativos_e_fotos_premium_pendentes.sql"));

        assertThat(sql)
                .contains("RENAME COLUMN singleton_id TO id")
                .contains("story_selecao_administrativa_anuncio_ativo_uk")
                .contains("story_selecao_administrativa_idempotency_uk")
                .contains("ativacao_beneficio_pendente_anuncio_beneficio_uk")
                .contains("WHERE status = 'AGUARDANDO_MODERACAO' AND anuncio_id IS NOT NULL")
                .doesNotContain("INSERT INTO story_selecao_administrativa")
                .doesNotContain("DELETE FROM story_selecao_administrativa")
                .doesNotContain("UPDATE ativacao_beneficio");
    }
}
