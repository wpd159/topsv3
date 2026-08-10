package br.com.topsdojob.v3.persistence.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminRelatorioReceitaJdbcRepositoryContractTest {

    @Test
    void receitaTemPagamentoComoFonteUnicaSemNMaisUmOuEventosDuplicados() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "admin", "AdminRelatorioReceitaJdbcRepository.java"));

        assertThat(source)
                .contains("p.status_interno = 'APROVADO'")
                .contains("sum(p.valor)")
                .contains("JOIN usuario")
                .contains("LEFT JOIN plano_credito")
                .contains("u.id AS usuario_id")
                .contains("u.email_normalizado AS usuario_email")
                .contains("u.telefone_normalizado AS usuario_whatsapp")
                .doesNotContain("pagamento_evento")
                .doesNotContain("pagamento_webhook")
                .doesNotContain("saldo_credito_usuario")
                .doesNotContain("ativacao_beneficio")
                .doesNotContain("movimento_credito mc");
    }
}
