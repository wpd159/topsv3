package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class V053PreviewRestritoMigrationTest {

  @Test
  void migrationEhAditivaFailClosedESemAcessoRemoto() throws IOException {
    String sql = Files.readString(migration()).toUpperCase(Locale.ROOT);

    assertThat(sql)
        .contains("ALTER TABLE ARQUIVO_MIDIA")
        .contains("ADD COLUMN PREVIEW_RESTRITO_TIPO")
        .contains("ADD COLUMN PREVIEW_RESTRITO_CHAVE")
        .contains("ADD COLUMN PREVIEW_RESTRITO_PIPELINE_VERSAO")
        .contains("ADD COLUMN PREVIEW_RESTRITO_STATUS")
        .contains("ADD COLUMN PREVIEW_RESTRITO_CONFIRMADO_EM")
        .contains("DEFAULT 'DESCONHECIDO'")
        .contains("CREATE INDEX ARQUIVO_MIDIA_PREVIEW_RESTRITO_STATUS_IDX")
        .doesNotContain("HTTP://")
        .doesNotContain("HTTPS://")
        .doesNotContain("R2.DEV")
        .doesNotMatch("(?s).*\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b.*");
  }

  private Path migration() {
    Path current = Path.of("").toAbsolutePath().normalize();
    Path direct = current.resolve(
        "src/main/resources/db/migration/V053__estado_preview_restrito.sql");
    if (Files.isRegularFile(direct)) {
      return direct;
    }
    Path nested = current.resolve(
        "backend/src/main/resources/db/migration/V053__estado_preview_restrito.sql");
    assertThat(nested).isRegularFile();
    return nested;
  }
}
