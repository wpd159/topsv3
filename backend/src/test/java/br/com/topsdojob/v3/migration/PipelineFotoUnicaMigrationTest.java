package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PipelineFotoUnicaMigrationTest {

  @Test
  void v030EhAditivaSemReferenciaPermanenteAoOriginal() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration", "V030__pipeline_foto_unica.sql"));

    assertThat(sql)
        .contains("ADD COLUMN pipeline_versao integer")
        .contains("ADD COLUMN marca_dagua_versao text")
        .contains("ADD COLUMN processado_em timestamptz")
        .contains("ADD COLUMN sha256_origem text")
        .contains("arquivo_midia_pipeline_consistencia_chk")
        .doesNotContain("original_bucket")
        .doesNotContain("original_chave_objeto")
        .doesNotContain("CREATE TABLE arquivo_midia")
        .doesNotContain("UPDATE arquivo_midia")
        .doesNotContain("DROP ");
  }
}
