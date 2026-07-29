package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AvisosV3MigrationTest {

  private static final Path MIGRATION = Path.of(
      "src", "main", "resources", "db", "migration",
      "V042__avisos_administrativos.sql");

  @Test
  void v042CriaFonteUnicaSemComunicacaoExternaOuSegmentacaoInventada() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("CREATE TABLE aviso_administrativo")
        .contains("'SITE', 'LOGIN_POPUP', 'ANUNCIO_RODAPE'")
        .contains("'SEMPRE', 'UMA_VEZ', 'DIARIO'")
        .contains("'RASCUNHO', 'PUBLICADO', 'ARQUIVADO'")
        .contains("versao bigint NOT NULL DEFAULT 0")
        .contains("WHERE status = 'PUBLICADO'")
        .contains("criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id)")
        .doesNotContain("INSERT INTO aviso_administrativo")
        .doesNotContain("ON DELETE CASCADE")
        .doesNotContain("email")
        .doesNotContain("push")
        .doesNotContain("sms");
  }
}
