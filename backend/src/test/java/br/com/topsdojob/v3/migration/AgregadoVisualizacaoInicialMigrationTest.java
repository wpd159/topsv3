package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AgregadoVisualizacaoInicialMigrationTest {

  @Test
  void v029CriaAgregadoAditivoUnicoAuditavelENaoNegativo() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V029__agregado_visualizacao_inicial.sql"));

    assertThat(sql)
        .contains("CREATE TABLE agregado_visualizacao_inicial")
        .contains("anuncio_id uuid NOT NULL REFERENCES anuncio (id)")
        .contains("execucao_id uuid NOT NULL REFERENCES importacao_execucao (id)")
        .contains("UNIQUE (anuncio_id)")
        .contains("CHECK (total_visualizacoes >= 0)")
        .contains("snapshot_fingerprint")
        .contains("origem_hash")
        .contains("snapshot_corte_em timestamptz NOT NULL")
        .doesNotContain("ALTER TABLE evento_visualizacao")
        .doesNotContain("CREATE TABLE importacao_execucao")
        .doesNotContain("DROP TABLE");
  }
}
