package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class StoriesIndependentesV050MigrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration");
  private static final Path MIGRATION = MIGRATIONS.resolve(
      "V050__stories_independentes_e_encerramento_logico.sql");

  @Test
  void adicionaMidiaDiretaEEncerramentoSemReescreverDadosOuPreco() throws Exception {
    String sql = Files.readString(MIGRATION);
    String executable = sql.lines()
        .map(line -> line.replaceFirst("--.*$", ""))
        .reduce("", (left, right) -> left + "\n" + right)
        .toUpperCase(Locale.ROOT);

    try (var files = Files.list(MIGRATIONS)) {
      assertThat(files
          .map(path -> path.getFileName().toString())
          .filter(name -> name.startsWith("V050__")))
          .containsExactly("V050__stories_independentes_e_encerramento_logico.sql");
    }
    assertThat(sql)
        .contains("arquivo_midia_id uuid REFERENCES arquivo_midia (id)")
        .contains("encerrado_em timestamptz")
        .contains("direito_preservado boolean NOT NULL DEFAULT false")
        .contains("FOREIGN KEY (ativacao_beneficio_id, criado_por)")
        .contains("modo_conteudo = 'ANUNCIO'")
        .contains("modo_conteudo = 'MIDIA_UPLOAD'")
        .contains("anuncio_id IS NULL AND anuncio_midia_id IS NULL AND arquivo_midia_id IS NOT NULL")
        .contains("ON story_anuncio (anuncio_id)")
        .contains("WHERE modo_conteudo = 'ANUNCIO'")
        .contains("ON story_anuncio (arquivo_midia_id)")
        .contains("ON story_anuncio (criado_por, encerrado_em, criado_em DESC, id DESC)")
        .doesNotContain("ON story_anuncio (criado_por)\n  WHERE modo_conteudo = 'ANUNCIO'")
        .doesNotContain("ON DELETE CASCADE");
    assertThat(executable)
        .doesNotContain("UPDATE ")
        .doesNotContain("DELETE ")
        .doesNotContain("TRUNCATE ")
        .doesNotContain("INSERT ")
        .doesNotContain("PRECO")
        .doesNotContain("VALOR_CREDITOS");
  }
}
