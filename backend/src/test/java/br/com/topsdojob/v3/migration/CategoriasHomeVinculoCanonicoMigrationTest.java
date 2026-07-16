package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CategoriasHomeVinculoCanonicoMigrationTest {

  @Test
  void restringeVinculoEDestinoEArmazenaSomenteAChaveInternaDaImagem() throws Exception {
    String sql = Files.readString(Path.of(
        "src", "main", "resources", "db", "migration",
        "V027__vinculo_canonico_categorias_home.sql"));

    assertThat(sql)
        .contains("ADD COLUMN imagem_object_key text")
        .contains("categoria_home_destino_canonico_chk")
        .contains("destino = '/anuncios?categoria=' || categoria_enum")
        .contains("CREATE UNIQUE INDEX categoria_home_categoria_ativa_uk")
        .contains("WHERE ativo")
        .contains("ACOMPANHANTE_FEMININA")
        .contains("VENDA_DE_CONTEUDO")
        .doesNotContain("DELETE FROM")
        .doesNotContain("INSERT INTO")
        .doesNotContain("http://")
        .doesNotContain("https://");
  }
}
