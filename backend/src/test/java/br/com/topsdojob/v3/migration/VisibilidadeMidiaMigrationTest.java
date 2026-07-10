package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VisibilidadeMidiaMigrationTest {

    @Test
    void v018MantemFonteUnicaEBackfillConservador() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V018__visibilidade_individual_midia.sql"));

        assertThat(sql)
                .contains("ADD COLUMN visibilidade_midia text")
                .contains("WHEN am.tipo IN ('VIDEO', 'STORY') THEN 'RESTRITA_18'")
                .contains("WHEN am.status = 'PENDENTE' THEN NULL")
                .contains("ELSE 'RESTRITA_18'")
                .contains("status <> 'PUBLICAVEL' OR visibilidade_midia IS NOT NULL")
                .contains("tipo = 'FOTO' OR visibilidade_midia = 'RESTRITA_18'")
                .contains("ALTER TABLE anuncio_midia DROP COLUMN classificacao_conteudo")
                .contains("ALTER TABLE arquivo_midia DROP COLUMN classificacao_conteudo")
                .contains("ALTER TABLE anuncio DROP COLUMN classificacao_conteudo");

        assertThat(count(sql, "ADD COLUMN visibilidade_midia")).isEqualTo(1);
    }

    private int count(String text, String fragment) {
        return (text.length() - text.replace(fragment, "").length()) / fragment.length();
    }
}
