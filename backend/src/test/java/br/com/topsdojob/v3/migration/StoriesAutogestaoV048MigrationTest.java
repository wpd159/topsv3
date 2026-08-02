package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StoriesAutogestaoV048MigrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration");
    private static final Path MIGRATION = MIGRATIONS.resolve(
            "V048__stories_autogestao_modos_conteudo.sql");

    @Test
    void preservaHistoricoEFechaInvariantesDaAutogestao() throws Exception {
        String sql = Files.readString(MIGRATION);
        String executable = sql.lines()
                .map(line -> line.replaceFirst("--.*$", ""))
                .reduce("", (left, right) -> left + "\n" + right)
                .toUpperCase(Locale.ROOT);

        try (var files = Files.list(MIGRATIONS)) {
            assertThat(files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V048__")))
                    .containsExactly("V048__stories_autogestao_modos_conteudo.sql");
        }
        assertThat(sql)
                .contains("ALTER COLUMN anuncio_midia_id DROP NOT NULL")
                .contains("modo_conteudo IS NULL")
                .contains("anuncio_id IS NULL")
                .contains("request_fingerprint IS NULL")
                .contains("modo_conteudo = 'ANUNCIO'")
                .contains("anuncio_midia_id IS NULL")
                .contains("modo_conteudo = 'MIDIA_UPLOAD'")
                .contains("request_fingerprint ~ '^[0-9a-f]{64}$'")
                .contains("FOREIGN KEY (anuncio_id, criado_por)")
                .contains("FOREIGN KEY (anuncio_id, anuncio_midia_id)")
                .contains("FOREIGN KEY (ativacao_beneficio_id, anuncio_id, criado_por)")
                .contains("story_anuncio_autogestao_idempotencia_uk")
                .contains("story_anuncio_autogestao_ativacao_uk")
                .contains("story_anuncio_autogestao_status_ativo_uk")
                .contains("ON story_anuncio (anuncio_id, idempotency_key)")
                .contains("ON story_anuncio (anuncio_id)")
                .contains("WHERE modo_conteudo IS NOT NULL")
                .doesNotContain("ON story_anuncio (criado_por, idempotency_key)")
                .doesNotContain("ON DELETE CASCADE");
        assertThat(executable)
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE ")
                .doesNotContain("TRUNCATE ");
    }
}
