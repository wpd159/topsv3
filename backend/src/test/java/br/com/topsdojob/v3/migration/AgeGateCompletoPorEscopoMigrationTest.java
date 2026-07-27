package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgeGateCompletoPorEscopoMigrationTest {

  @Test
  void v034CriaEstruturasAditivasSemPersistirDadosPessoaisOuTokenBruto()
      throws Exception {
    String sql = Files.readString(Path.of(
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "V034__age_gate_completo_por_escopo.sql"));

    assertThat(sql)
        .contains("ALTER TABLE evento_verificacao_etaria")
        .contains("CREATE TABLE IF NOT EXISTS compliance_visitor_challenge")
        .contains("CREATE TABLE IF NOT EXISTS compliance_visitor_token")
        .contains("CREATE TABLE IF NOT EXISTS compliance_visitor_risk_profile")
        .contains("CREATE TABLE IF NOT EXISTS compliance_visitor_documento")
        .contains("idempotencia_hash text NOT NULL")
        .contains("token_hash text NOT NULL")
        .contains("user_agent_hash text NOT NULL")
        .contains("UNIQUE INDEX IF NOT EXISTS compliance_token_hash_uk")
        .contains("UNIQUE INDEX IF NOT EXISTS compliance_token_challenge_scope_uk")
        .contains("UNIQUE INDEX IF NOT EXISTS compliance_documento_session_idempotencia_uk")
        .contains("UNIQUE INDEX IF NOT EXISTS compliance_documento_pendente_challenge_uk")
        .contains("evento_verificacao_etaria_challenge_fk")
        .contains("CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 12582912)")
        .doesNotContain("token_bruto")
        .doesNotContain("cpf ")
        .doesNotContain("data_nascimento")
        .doesNotContain("DROP TABLE")
        .doesNotContain("DELETE FROM")
        .doesNotContain("TRUNCATE");
  }
}
