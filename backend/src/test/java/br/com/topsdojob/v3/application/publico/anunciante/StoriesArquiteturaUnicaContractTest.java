package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StoriesArquiteturaUnicaContractTest {

  private static final Path MAIN = Path.of("src", "main");
  private static final Path FRONTEND = Path.of("..", "frontend", "src");
  private static final Path OPENAPI = Path.of("..", "contracts", "openapi", "topsdojob-v3-local.yaml");

  @Test
  void publicacaoPossuiUmAgregadoUmRepositoryEUmWriterCanonico() throws Exception {
    Map<Path, String> java = fontes(MAIN.resolve("java"), path -> path.toString().endsWith(".java"));
    String runtime = String.join("\n", java.values());
    String writer = java.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith("MinhaContaStoriesPublicacaoService.java"))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow();

    assertThat(contar(runtime, "StoryAnuncioEntity.criarAnuncio(")).isEqualTo(1);
    assertThat(contar(runtime, "StoryAnuncioEntity.criarMidiaUpload(")).isEqualTo(1);
    assertThat(writer)
        .contains("StoryAnuncioEntity.criarAnuncio(")
        .contains("StoryAnuncioEntity.criarMidiaUpload(")
        .contains("storyRepository.save(story)")
        .doesNotContain("AnuncioMidiaRepository")
        .doesNotContain("FinalidadeAnuncioMidia");

    String fixtureStory = String.join("", "StoryAnuncioEntity", ".", "criarFixture", "Homologacao", "(");
    String oldSelectionFactory = String.join("", "StorySelecaoAdministrativaEntity", ".", "nova", "(");
    assertThat(runtime).doesNotContain(fixtureStory, oldSelectionFactory);

    Set<String> classes = java.keySet().stream()
        .map(path -> path.getFileName().toString().replace(".java", ""))
        .collect(Collectors.toSet());
    assertThat(classes).doesNotContain(
        String.join("", "Meu", "Anuncio", "Story", "Service"),
        String.join("", "Meu", "Anuncio", "Story", "Ativacao", "Service"),
        String.join("", "Meu", "Anuncio", "Story", "Oferta", "Service"),
        String.join("", "Minha", "Conta", "Story", "Publicacao", "Service"),
        String.join("", "Story", "Publico", "Service"));
  }

  @Test
  void v050EvoluiSomenteStoryAnuncioSemDmlOuSingletonPorUsuario() throws Exception {
    String migration = Files.readString(MAIN.resolve(Path.of(
        "resources", "db", "migration", "V050__stories_independentes_e_encerramento_logico.sql")),
        StandardCharsets.UTF_8);

    assertThat(migration)
        .contains("story_anuncio")
        .doesNotContainPattern("(?i)\\b(?:INSERT|UPDATE|DELETE)\\b")
        .doesNotContainPattern("(?i)create\\s+table\\s+story_(?:v2|conta|independente)")
        .doesNotContainPattern("(?is)create\\s+unique\\s+index\\s+\\S*(?:status|ativo)\\S*.{0,220}on\\s+story_anuncio\\s*\\(criado_por\\)")
        .doesNotContain("custo_creditos", "duracao_dias");
  }

  @Test
  void controllersOpenApiESegurancaExibemSomenteFamiliasCanonicas() throws Exception {
    String controller = Files.readString(MAIN.resolve(Path.of(
        "java", "br", "com", "topsdojob", "v3", "web", "publico", "anunciante",
        "MinhaContaStoriesController.java")), StandardCharsets.UTF_8);
    String publicController = Files.readString(MAIN.resolve(Path.of(
        "java", "br", "com", "topsdojob", "v3", "web", "publico", "AnuncioPublicoController.java")),
        StandardCharsets.UTF_8);
    String security = Files.readString(MAIN.resolve(Path.of(
        "java", "br", "com", "topsdojob", "v3", "security", "config", "SecurityConfig.java")),
        StandardCharsets.UTF_8);
    String openapi = Files.readString(OPENAPI, StandardCharsets.UTF_8);
    String oldSecurity = String.join("", "/api/public/minha-conta", "/anuncios/", "*", "/stories");
    String oldPerAd = String.join("", "/api/public/minha-conta", "/anuncios/", "{slug}", "/stories");
    String oldPublicPerAd = String.join("", "/api/public/anuncios/", "{slug}", "/stories");
    String oldAdmin = String.join("", "/api/admin", "/stories/", "selecao");

    assertThat(controller)
        .contains("@RequestMapping(\"/api/public/minha-conta/stories\")")
        .contains("publicacaoService.publicar(")
        .contains("direitoService.ativar(")
        .contains("encerramentoService.encerrarProprio(");
    assertThat(openapi)
        .contains("/api/public/minha-conta/stories:")
        .contains("/api/admin/stories/gestao:")
        .contains("/api/admin/stories/{storyId}/remover:")
        .doesNotContain(oldPerAd, oldPublicPerAd, oldAdmin);
    assertThat(publicController)
        .doesNotContain("@GetMapping(\"/{slug}/stories\")")
        .doesNotContain("StoryPublicoService");
    assertThat(security)
        .contains("/api/admin/stories/*/remover")
        .doesNotContain(oldAdmin)
        .doesNotContain(oldSecurity);
  }

  @Test
  void frontendPossuiUmAdapterUmModalEUmaMaquinaDeCriacao() throws Exception {
    Map<Path, String> frontend = fontes(
        FRONTEND,
        path -> path.toString().endsWith(".ts") || path.toString().endsWith(".tsx"));
    String runtime = String.join("\n", frontend.values());
    String adapter = frontend.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith("minha-conta-stories-api.ts"))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow();
    String dialog = frontend.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith("story-create-dialog.tsx"))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow();
    List<String> creationDialogs = frontend.keySet().stream()
        .map(path -> path.getFileName().toString())
        .filter(name -> name.matches("story-.*(?:create|publication|upload|selector)-dialog\\.tsx"))
        .toList();
    String oldPerAd = String.join("", "/minha-conta", "/anuncios/", "${", "slug", "}", "/stories");

    assertThat(contar(runtime, "const STORY_ROOT =")).isEqualTo(1);
    assertThat(adapter)
        .contains("const STORY_ROOT = '/minha-conta/stories'")
        .contains("form.append('modoConteudo', modoConteudo)")
        .contains("modoConteudo === 'ANUNCIO' && anuncioId")
        .contains("modoConteudo === 'MIDIA_UPLOAD' && arquivo");
    assertThat(creationDialogs).containsExactly("story-create-dialog.tsx");
    assertThat(dialog)
        .contains("type Step =")
        .contains("chooseMode('ANUNCIO')")
        .contains("chooseMode('MIDIA_UPLOAD')")
        .doesNotContain("fetch(", "XMLHttpRequest");
    assertThat(runtime).doesNotContain(oldPerAd);
  }

  private static Map<Path, String> fontes(Path root, Predicate<Path> filter) throws IOException {
    try (var paths = Files.walk(root)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(filter)
          .collect(Collectors.toMap(
              path -> path,
              path -> read(path)));
    }
  }

  private static String read(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static int contar(String source, String token) {
    int count = 0;
    int index = 0;
    while ((index = source.indexOf(token, index)) >= 0) {
      count++;
      index += token.length();
    }
    return count;
  }
}
