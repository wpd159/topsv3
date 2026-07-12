package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CategoriasHomeIdadePublicaMigrationTest {

  @Test
  void v021CriaFonteCanonicaDeCategoriasEPersisteNascimentoPrivado() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V021__categorias_home_idade_publica.sql"));

    assertThat(sql)
        .contains("ADD COLUMN data_nascimento date")
        .contains("CREATE TABLE categoria_home")
        .contains("imagem_publica_url")
        .contains("categoria_home_ativas_ordem_idx")
        .doesNotContain("INSERT INTO")
        .doesNotContain("storage_key")
        .doesNotContain("data_nascimento text");
  }
}
