package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SuporteV047MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V047__suporte_sugestao_e_mensagens_nao_lidas.sql");

    @Test
    void ampliaSchemaCanonicoSemDuplicarOuMigrarFeedbackHistorico() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'SUGESTAO'")
                .contains("nao_lida_usuario boolean NOT NULL DEFAULT false")
                .contains("mensagem_suporte_nao_lida_usuario_idx")
                .doesNotContain("feedback_sugestao")
                .doesNotContain("CREATE TABLE")
                .doesNotContain("UPDATE ")
                .doesNotContain("DELETE ")
                .doesNotContain("CASCADE");
    }
}
