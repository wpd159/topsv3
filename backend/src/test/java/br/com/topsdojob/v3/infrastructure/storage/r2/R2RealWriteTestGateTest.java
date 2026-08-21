package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class R2RealWriteTestGateTest {

  @Test
  void exigeTodasAsQuatroCondicoes() {
    Map<String, String> environment = validEnvironment();

    for (String required : new String[] {
        "R2_REAL_WRITE_TESTS_ENABLED",
        "R2_TEST_BUCKET",
        "R2_TEST_PREFIX",
        "R2_TEST_CLEANUP_CONFIRMED"
    }) {
      Map<String, String> incomplete = new HashMap<>(environment);
      incomplete.remove(required);

      assertThat(R2RealWriteTestGate.evaluate(incomplete).enabled())
          .as(required)
          .isFalse();
    }
  }

  @Test
  void rejeitaTodosOsBucketsOperacionaisPorCorrespondenciaExata() {
    for (String bucket : R2RealWriteTestGate.OPERATIONAL_BUCKETS) {
      Map<String, String> environment = validEnvironment();
      environment.put("R2_TEST_BUCKET", bucket);

      var decision = R2RealWriteTestGate.evaluate(environment);

      assertThat(decision.enabled()).as(bucket).isFalse();
      assertThat(decision.reason()).contains("operacional");
    }
  }

  @Test
  void rejeitaBucketVazioPrefixoRaizEOuSemUuid() {
    Map<String, String> blankBucket = validEnvironment();
    blankBucket.put("R2_TEST_BUCKET", "  ");
    assertThat(R2RealWriteTestGate.evaluate(blankBucket).enabled()).isFalse();

    for (String prefix : new String[] {"", "/", "test-runs/", "test-runs/fixo/"}) {
      Map<String, String> environment = validEnvironment();
      environment.put("R2_TEST_PREFIX", prefix);
      assertThat(R2RealWriteTestGate.evaluate(environment).enabled())
          .as(prefix)
          .isFalse();
    }
  }

  @Test
  void aceitaSomentePrefixoIsoladoComUuid() {
    Map<String, String> environment = validEnvironment();

    var decision = R2RealWriteTestGate.evaluate(environment);

    assertThat(decision.enabled()).isTrue();
    assertThat(decision.configuration().bucket()).isEqualTo("topsdojob-testes-isolados");
    assertThat(decision.configuration().prefix()).startsWith("test-runs/").endsWith("/");
  }

  @Test
  void chaveDeveEstarAbaixoDoPrefixoDaExecucao() {
    String prefix = "test-runs/" + UUID.randomUUID() + "/";

    R2RealWriteTestGate.validateKey(prefix, prefix + "public/smoke.txt");

    assertThatThrownBy(() -> R2RealWriteTestGate.validateKey(prefix, prefix))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> R2RealWriteTestGate.validateKey(prefix, "outro/smoke.txt"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Map<String, String> validEnvironment() {
    Map<String, String> environment = new HashMap<>();
    environment.put("R2_REAL_WRITE_TESTS_ENABLED", "true");
    environment.put("R2_TEST_BUCKET", "topsdojob-testes-isolados");
    environment.put("R2_TEST_PREFIX", "test-runs/" + UUID.randomUUID() + "/");
    environment.put("R2_TEST_CLEANUP_CONFIRMED", "true");
    return environment;
  }
}