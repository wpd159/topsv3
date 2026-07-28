package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FaqsV3MigrationTest {

  private static final Path MIGRATION = Path.of(
      "src", "main", "resources", "db", "migration",
      "V040__faqs_administrativas_publicas.sql");

  @Test
  void v040CriaFonteUnicaComPublicacaoEVersionamentoSemConteudoHardcoded() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("CREATE TABLE faq_item")
        .contains("'RASCUNHO', 'PUBLICADO', 'ARQUIVADO'")
        .contains("'GERAL', 'CONTA', 'PAGAMENTOS', 'SEGURANCA', 'ANUNCIOS'")
        .contains("versao bigint NOT NULL DEFAULT 0")
        .contains("WHERE status = 'PUBLICADO'")
        .contains("criado_por_usuario_id uuid NOT NULL REFERENCES usuario (id)")
        .doesNotContain("INSERT INTO faq_item")
        .doesNotContain("ON DELETE CASCADE");
  }
}
