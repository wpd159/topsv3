package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KycDocumentosPrivadosMigrationTest {

  @Test
  void v023ConsolidaCpfEnvioPartesEstadosERbacDocumental() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V023__kyc_documentos_privados.sql"));

    assertThat(sql)
        .contains("ADD COLUMN nome_civil")
        .contains("ADD COLUMN cpf_normalizado")
        .contains("CREATE UNIQUE INDEX usuario_cpf_normalizado_uk")
        .contains("ADD COLUMN envio_id")
        .contains("ADD COLUMN parte")
        .contains("'AJUSTE_SOLICITADO'")
        .contains("documento_usuario_envio_parte_uk")
        .contains("'DOCUMENTO_REVISAR'")
        .contains("'ADMIN'")
        .contains("'MODERADOR'")
        .contains("ON CONFLICT (papel, permissao_id) DO NOTHING")
        .doesNotContain("'USUARIO', permissao.id");
  }
}
