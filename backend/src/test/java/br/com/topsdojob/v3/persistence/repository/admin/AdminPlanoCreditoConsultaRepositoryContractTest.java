package br.com.topsdojob.v3.persistence.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminPlanoCreditoConsultaRepositoryContractTest {

    @Test
    void contaSomentePagamentosAprovadosSemAlterarFinanceiro() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "persistence", "repository", "admin", "AdminPlanoCreditoConsultaRepository.java"));

        assertThat(source)
                .contains("FROM pagamento")
                .contains("status_interno = 'APROVADO'")
                .contains("GROUP BY plano_credito_id")
                .doesNotContain("UPDATE pagamento")
                .doesNotContain("DELETE FROM pagamento")
                .doesNotContain("movimento_credito")
                .doesNotContain("webhook");
    }
}
