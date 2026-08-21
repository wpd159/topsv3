package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class R2WriteSafetyContractTest {

  private static final Pattern READ_ONLY_NAME = Pattern.compile(
      ".*(DryRun|Plan|Preview|Audit|Validate|Check).*\\.java");
  private static final List<String> FORBIDDEN_WRITES = List.of(
      ".put(",
      ".putIfAbsent(",
      ".copy(",
      ".delete(",
      "PutObject",
      "CopyObject",
      "DeleteObject",
      "multipart",
      "transport.transport(");

  @Test
  void dryRunsEPlanosR2NaoPossuemChamadasDeEscrita() throws Exception {
    Path packageRoot = Path.of(
        "src/test/java/br/com/topsdojob/v3/infrastructure/storage/r2");
    List<Path> sources;
    try (var files = Files.list(packageRoot)) {
      sources = files
          .filter(Files::isRegularFile)
          .filter(path -> READ_ONLY_NAME.matcher(path.getFileName().toString()).matches())
          .toList();
    }

    assertThat(sources).isNotEmpty();
    for (Path source : sources) {
      String content = Files.readString(source, StandardCharsets.UTF_8);
      assertThat(content)
          .as(source.getFileName().toString())
          .doesNotContain(FORBIDDEN_WRITES.toArray(String[]::new));
    }
  }

  @Test
  void testeDeEscritaRealEManualEGated() throws Exception {
    Path source = Path.of(
        "src/test/java/br/com/topsdojob/v3/infrastructure/storage/r2/"
            + "R2ObjectStorageRealWriteIT.java");
    String content = Files.readString(source, StandardCharsets.UTF_8);
    Path gateSource = Path.of(
        "src/test/java/br/com/topsdojob/v3/infrastructure/storage/r2/"
            + "R2RealWriteTestGate.java");
    String gateContent = Files.readString(gateSource, StandardCharsets.UTF_8);

    assertThat(source.getFileName().toString()).endsWith("IT.java");
    assertThat(content)
        .contains("R2RealWriteTestGate", "R2RealWriteTestStorage")
        .doesNotContain("R2_IT_ENABLED");
    assertThat(gateContent)
        .contains(
            "R2_REAL_WRITE_TESTS_ENABLED",
            "R2_TEST_BUCKET",
            "R2_TEST_PREFIX",
            "R2_TEST_CLEANUP_CONFIRMED")
        .doesNotContain("R2_IT_ENABLED");
  }

  @Test
  void workflowsNaoRecebemCredenciaisNemExecutamTestesReaisR2() throws Exception {
    Path workflows = Path.of("../.github/workflows");
    try (var files = Files.list(workflows)) {
      for (Path workflow : files.filter(Files::isRegularFile).toList()) {
        String content = Files.readString(workflow, StandardCharsets.UTF_8);
        assertThat(content)
            .as(workflow.getFileName().toString())
            .doesNotContain(
                "R2_REAL_WRITE_TESTS_ENABLED",
                "R2_TEST_BUCKET",
                "R2_TEST_PREFIX",
                "R2_TEST_CLEANUP_CONFIRMED",
                "R2_ACCESS_KEY",
                "R2_SIGNING_VALUE",
                "R2ObjectStorageRealWriteIT");
      }
    }
  }
}