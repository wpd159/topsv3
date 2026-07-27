package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RbacComplianceVisitanteMigrationTest {

  @Test
  void v035CatalogaSegurancaGerenciarEVinculaSomenteAoAdmin() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V035__rbac_compliance_visitante.sql"));

    assertThat(sql)
        .contains("INSERT INTO permissao")
        .contains("'SEGURANCA_GERENCIAR'")
        .contains("INSERT INTO papel_permissao")
        .contains("'ADMIN'")
        .contains("ON CONFLICT (codigo) DO UPDATE")
        .contains("ON CONFLICT (papel, permissao_id) DO NOTHING")
        .doesNotContain("'MODERADOR'")
        .doesNotContain("'USUARIO'")
        .doesNotContain("'COMERCIAL'")
        .doesNotContain("DELETE FROM")
        .doesNotContain("UPDATE papel_permissao");
  }
}
