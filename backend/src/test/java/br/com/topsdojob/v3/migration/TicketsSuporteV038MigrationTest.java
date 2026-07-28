package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TicketsSuporteV038MigrationTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V038__tickets_suporte_funcionais.sql");

    @Test
    void completaSchemaExistenteSemCriarArquiteturaParalela() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("ALTER TABLE ticket_suporte")
                .contains("ALTER TABLE mensagem_suporte")
                .contains("categoria text")
                .contains("criacao_idempotency_key")
                .contains("idempotency_key varchar(160)")
                .contains("ON ticket_suporte (usuario_id, criacao_idempotency_key)")
                .contains("ON mensagem_suporte (autor_usuario_id, idempotency_key)")
                .doesNotContain("CREATE TABLE ticket_suporte")
                .doesNotContain("CREATE TABLE mensagem_suporte")
                .doesNotContain("anexo")
                .doesNotContain("CASCADE")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void provisionaSuporteSomenteParaAdminEModerador() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'SUPORTE_ATENDER'")
                .contains("FROM (VALUES ('ADMIN'), ('MODERADOR')) AS papeis(papel)")
                .contains("ON CONFLICT (papel, permissao_id) DO NOTHING")
                .doesNotContain("('USUARIO')")
                .doesNotContain("('COMERCIAL')");
    }

    @Test
    void preservaAsQuatroCategoriasComprovadasNaProducao() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("'ERRO_NO_SISTEMA'")
                .contains("'PROBLEMAS_COM_PAGAMENTO'")
                .contains("'ACESSO_CONTA'")
                .contains("'OUTROS'");
    }
}
