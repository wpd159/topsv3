package br.com.topsdojob.v3.blog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BlogV037MigrationContractTest {

  @Test
  void migrationEAdicionaSomenteEstruturasEditoriais() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration", "V037__blog_editorial.sql"));

    assertThat(sql)
        .contains("CREATE TABLE blog_categoria")
        .contains("CREATE TABLE blog_imagem")
        .contains("CREATE TABLE blog_post")
        .contains("UNIQUE (slug)")
        .contains("WHERE status = 'PUBLICADO'")
        .doesNotContain("ALTER TABLE anuncio")
        .doesNotContain("ALTER TABLE usuario")
        .doesNotContain("ALTER TABLE documento")
        .doesNotContain("DROP TABLE");
  }
}
