package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BloqueioJuridicoAnuncioMigrationTest {

  @Test
  void v032AdicionaEstadoETrilhaJuridicaSemRemoverDadosHistoricos() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration", "V032__bloqueio_juridico_anuncio.sql"));

    assertThat(sql)
        .contains("'BLOQUEADO'")
        .contains("CREATE TABLE anuncio_bloqueio_juridico")
        .contains("anuncio_bloqueio_juridico_anuncio_ativo_uk")
        .contains("anuncio_bloqueio_juridico_usuario_ativo_uk")
        .contains("REFERENCES anuncio (id)")
        .contains("REFERENCES usuario (id)")
        .contains("escopo IN ('ANUNCIO', 'ANUNCIO_E_USUARIO')")
        .contains("anuncio_desbloqueado_em")
        .contains("usuario_desbloqueado_em")
        .doesNotContain("DROP TABLE")
        .doesNotContain("DELETE FROM")
        .doesNotContain("UPDATE anuncio SET")
        .doesNotContain("UPDATE usuario SET");
  }
}
