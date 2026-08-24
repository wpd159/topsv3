package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class R2TestPolicyTest {

  private static final Path R2_TEST_ROOT = Path.of(
      "src/test/java/br/com/topsdojob/v3/infrastructure/storage/r2");

  @Test
  void dryRunEPlanNaoContemOperacaoMutavel() throws Exception {
    List<Path> sources;
    try (var files = Files.list(R2_TEST_ROOT)) {
      sources = files
          .filter(path -> path.getFileName().toString().matches(".*(DryRun|Plan).*\\.java"))
          .toList();
    }

    assertThat(sources).isNotEmpty();
    for (Path source : sources) {
      String content = Files.readString(source, StandardCharsets.UTF_8);
      assertThat(content)
          .as(source.toString())
          .contains("new ReadOnlyObjectStorage")
          .doesNotContain(".put(", ".putIfAbsent(", ".delete(", ".transport(");
    }
  }

  @Test
  void testeRealFicaForaDaConvencaoDoMavenPadraoETemGateECleanup() throws Exception {
    Path realWrite = R2_TEST_ROOT.resolve("R2ObjectStorageRealWriteIT.java");
    String content = Files.readString(realWrite, StandardCharsets.UTF_8);

    assertThat(realWrite.getFileName().toString()).endsWith("IT.java").doesNotEndWith("Test.java");
    assertThat(content)
        .contains("R2RealWriteTestGate.evaluate")
        .contains("try (R2RealWriteTestStorage")
        .doesNotContain("topsdojob-docs", "topsdojob-fotos", "topsdojob-hml-");
    assertThat(Files.exists(R2_TEST_ROOT.resolve("R2ObjectStorageRealIntegrationTest.java")))
        .isFalse();
  }

  @Test
  void workflowsNaoInjetamCredenciaisNemAtivamTesteRealR2() throws Exception {
    Path workflows = Path.of("..", ".github", "workflows");
    try (var files = Files.list(workflows)) {
      for (Path workflow : files.filter(Files::isRegularFile).toList()) {
        String content = Files.readString(workflow, StandardCharsets.UTF_8);
        assertThat(content)
            .as(workflow.toString())
            .doesNotContain(
                "R2_REAL_WRITE_TESTS_ENABLED",
                "R2_TEST_BUCKET",
                "R2_TEST_PREFIX",
                "R2_TEST_CLEANUP_CONFIRMED",
                "R2_ACCESS_KEY",
                "R2_SIGNING_VALUE");
      }
    }
  }
}