package br.com.topsdojob.v3.infrastructure.storage.r2;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class R2RealWriteTestGate {

  static final Set<String> OPERATIONAL_BUCKETS = Set.of(
      "topsdojob-docs",
      "topsdojob-fotos",
      "topsdojob-hml-documentos",
      "topsdojob-hml-midias-privadas",
      "topsdojob-hml-midias-publicas");

  private R2RealWriteTestGate() {
  }

  static Decision evaluate(Map<String, String> environment) {
    if (!enabled(environment.get("R2_REAL_WRITE_TESTS_ENABLED"))) {
      return Decision.disabled("R2_REAL_WRITE_TESTS_ENABLED ausente");
    }
    if (!enabled(environment.get("R2_TEST_CLEANUP_CONFIRMED"))) {
      return Decision.disabled("R2_TEST_CLEANUP_CONFIRMED ausente");
    }
    String bucket = normalized(environment.get("R2_TEST_BUCKET"));
    if (bucket == null) {
      return Decision.disabled("R2_TEST_BUCKET ausente");
    }
    if (OPERATIONAL_BUCKETS.contains(bucket)) {
      return Decision.disabled("Bucket operacional bloqueado");
    }
    String prefix = normalized(environment.get("R2_TEST_PREFIX"));
    if (prefix == null) {
      return Decision.disabled("R2_TEST_PREFIX ausente");
    }
    UUID runId = runId(prefix);
    if (runId == null) {
      return Decision.disabled("R2_TEST_PREFIX deve usar test-runs/<UUID>/");
    }
    return Decision.enabled(new Configuration(bucket, prefix, runId));
  }

  static void validateKey(String prefix, String key) {
    if (prefix == null || key == null || key.equals(prefix) || !key.startsWith(prefix)) {
      throw new IllegalArgumentException("Chave fora do prefixo isolado do teste");
    }
  }

  private static UUID runId(String prefix) {
    String root = "test-runs/";
    if (!prefix.startsWith(root)
        || !prefix.endsWith("/")
        || prefix.length() <= root.length()) {
      return null;
    }
    String value = prefix.substring(root.length(), prefix.length() - 1);
    if (value.contains("/")) {
      return null;
    }
    try {
      UUID runId = UUID.fromString(value);
      return runId.toString().equals(value) ? runId : null;
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static boolean enabled(String value) {
    return "true".equals(value);
  }

  private static String normalized(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  record Configuration(String bucket, String prefix, UUID runId) {
  }

  record Decision(boolean enabled, String reason, Configuration configuration) {

    private static Decision disabled(String reason) {
      return new Decision(false, reason, null);
    }

    private static Decision enabled(Configuration configuration) {
      return new Decision(true, "habilitado", configuration);
    }
  }
}