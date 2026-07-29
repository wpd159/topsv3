package br.com.topsdojob.v3.wizard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WizardProgressContractTest {

  @Test
  void migrationEhMinimaSemConteudoPessoalDoWizard() throws Exception {
    String migration = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V043__progresso_wizard_anuncios.sql"));

    assertThat(migration)
        .contains("CREATE TABLE wizard_progresso")
        .contains("UNIQUE (usuario_id, sessao_id)")
        .contains("maior_step_ordem")
        .contains("REFERENCES usuario (id)")
        .contains("REFERENCES anuncio (id)")
        .doesNotContain("cpf")
        .doesNotContain("telefone")
        .doesNotContain("descricao")
        .doesNotContain("documento")
        .doesNotContain("premium_selecionado");
  }

  @Test
  void consultasSaoAgregadasESemNMaisUm() throws Exception {
    String repository = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3",
        "persistence", "repository", "wizard",
        "WizardProgressJdbcRepository.java"));

    assertThat(repository)
        .contains("count(*) FILTER")
        .contains("count(DISTINCT usuario_id)")
        .contains("count(*) OVER ()")
        .contains("GROUP BY ultimo_step")
        .doesNotContain("for (")
        .doesNotContain("findById(");
  }

  @Test
  void openApiPublicaOsDoisContratosSemDadosPessoais() throws Exception {
    String openApi = Files.readString(Path.of(
        "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));
    int dashboardStart = openApi.indexOf("    AdminWizardProgressDashboard:");
    String wizardSchemas = openApi.substring(openApi.indexOf("    WizardProgressSyncRequest:"));

    assertThat(openApi)
        .contains("/api/public/wizard-progress/sync:")
        .contains("operationId: syncWizardProgress")
        .contains("/api/admin/wizard-progress/dashboard:")
        .contains("operationId: getAdminWizardProgressDashboard");
    assertThat(dashboardStart).isPositive();
    assertThat(wizardSchemas)
        .doesNotContain("cpf:")
        .doesNotContain("telefone:")
        .doesNotContain("objectKey:")
        .doesNotContain("documento:");
  }
}
