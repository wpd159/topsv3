package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SugestoesV041MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V041__sugestoes_feedback_funcionais.sql");

    @Test
    void v041CriaPersistenciaAutenticadaIdempotenteSemRecursosInventados() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE feedback_sugestao")
                .contains("usuario_id uuid NOT NULL REFERENCES usuario")
                .contains("'FEATURE', 'BUG'")
                .contains("'PENDENTE', 'EM_ANALISE', 'RESOLVIDO', 'RECUSADO'")
                .contains("feedback_sugestao_idempotencia_uk")
                .contains("providencia_resumida")
                .doesNotContain("ON DELETE CASCADE")
                .doesNotContain("anexo")
                .doesNotContain("resposta_publica")
                .doesNotContain("UPDATE usuario");
    }
}
