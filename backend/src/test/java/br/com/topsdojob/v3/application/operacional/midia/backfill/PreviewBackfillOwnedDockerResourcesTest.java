package br.com.topsdojob.v3.application.operacional.midia.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;

class PreviewBackfillOwnedDockerResourcesTest {
  @Test
  void preservaErroPrimarioETentaTodasAsEtapasMesmoComCloseEFalhasAdicionais() {
    var original = new AssertionError("test failed first");
    var closing = new IllegalStateException("context close failed");
    var container = new IllegalStateException("container removal failed");
    var network = new IllegalStateException("network removal failed");
    List<String> attempted = new ArrayList<>();
    Throwable result = PreviewBackfillOwnedDockerResources.cleanupPreserving(original,
        () -> { attempted.add("context"); throw closing; },
        () -> { attempted.add("container"); throw container; },
        () -> { attempted.add("network"); throw network; },
        () -> attempted.add("absence"));
    assertThat(result).isSameAs(original);
    assertThat(result.getSuppressed()).containsExactly(closing, container, network);
    assertThat(attempted).containsExactly("context", "container", "network", "absence");
  }

  @Test
  void primeiroErroDeCleanupFalhaETemAdicionaisSemAutossupressao() {
    var first = new IllegalStateException("close failed");
    var next = new IllegalStateException("absence unproven");
    Throwable result = PreviewBackfillOwnedDockerResources.cleanupPreserving(null,
        () -> { throw first; }, () -> { throw first; }, () -> { throw next; });
    assertThat(result).isSameAs(first);
    assertThat(result.getSuppressed()).containsExactly(next);
  }

  @Test
  void cleanupRemoveIdsPropriosComVolumesEPreservaVolumeHistorico() throws Exception {
    FakeDocker docker = new FakeDocker();
    var fixture = fixture(docker);
    allocate(fixture, true);
    var before = fixture.snapshot();
    assertThat(before.containers()).hasSize(2);
    assertThat(before.volumes()).containsOnlyKeys(FakeDocker.VOLUME);
    fixture.close();
    fixture.verifyAbsent();
    assertThat(docker.commands).contains(List.of("rm", "--force", "--volumes", FakeDocker.PG));
    assertThat(docker.commands).contains(List.of("network", "rm", FakeDocker.NETWORK));
    assertThat(docker.historicalVolume).isTrue();
    int count = docker.commands.size();
    fixture.close();
    assertThat(docker.commands).hasSize(count);
  }

  @Test
  void ownershipDivergenteNaoRemoveContainerNemVolume() throws Exception {
    FakeDocker docker = new FakeDocker();
    var fixture = fixture(docker);
    allocate(fixture, false);
    docker.containerOwnerOverride = "somebody-else";
    assertThatThrownBy(fixture::close).hasMessageContaining("ownership");
    assertThat(docker.containers).containsKey(FakeDocker.PG);
    assertThat(docker.volume).isTrue();
    assertThat(docker.historicalVolume).isTrue();
    assertThat(docker.commands).doesNotContain(List.of("rm", "--force", "--volumes", FakeDocker.PG));
  }

  @Test
  void falhaDeRemocaoNaoImpedeOutrasEtapasNemGeraSucesso() throws Exception {
    FakeDocker docker = new FakeDocker();
    var fixture = fixture(docker);
    allocate(fixture, true);
    docker.failPgRemoval = true;
    assertThatThrownBy(fixture::close).hasMessageContaining("exit=9")
        .satisfies(error -> assertThat(error.getSuppressed().length).isGreaterThanOrEqualTo(2));
    assertThat(docker.containers).containsOnlyKeys(FakeDocker.PG); // Flyway was independently removed.
    assertThat(docker.commands).contains(List.of("network", "rm", FakeDocker.NETWORK));
    assertThat(docker.commands.stream().anyMatch(args -> args.contains("volume=" + FakeDocker.VOLUME))).isTrue();
    assertThat(docker.historicalVolume).isTrue();
  }

  @Test
  void respostaPerdidaDescobreIdPeloOwnerMasMantemAmbiguidadeMesmoAposAusencia() throws Exception {
    FakeDocker docker = new FakeDocker();
    var fixture = fixture(docker);
    fixture.createNetwork();
    docker.loseCreateResponse = true;
    assertThatThrownBy(() -> fixture.createContainer("postgres", Map.of(), "postgres:17-alpine"))
        .isInstanceOf(PreviewBackfillOwnedDockerResources.AmbiguousCommand.class);
    assertThatThrownBy(fixture::close)
        .isInstanceOf(PreviewBackfillOwnedDockerResources.AmbiguousCommand.class);
    fixture.verifyAbsent();
    assertThat(docker.containers).isEmpty();
    assertThat(docker.volume).isFalse();
    assertThat(docker.historicalVolume).isTrue();
  }

  @Test
  void ausenciaNaoComprovadaNaoViraCleanupVerde() throws Exception {
    FakeDocker docker = new FakeDocker();
    var fixture = fixture(docker);
    allocate(fixture, false);
    docker.ghostPgPresence = true;
    assertThatThrownBy(fixture::close).hasMessageContaining("container remains");
  }

  @ParameterizedTest
  @ValueSource(strings = {"normal", "preparation_failure", "context_close_failure"})
  @EnabledIfEnvironmentVariable(named = "PREVIEW_BACKFILL_POSTGRES17_ENABLED", matches = "true")
  void cicloRealConfirmaContainerVolumeAnonimoERedeAusentes(String scenario) throws Throwable {
    var fixture = new PreviewBackfillOwnedDockerResources(
        "topsv3-preview-cleanup-" + UUID.randomUUID().toString().replace("-", ""));
    Throwable testFailure = null;
    try {
      fixture.createNetwork();
      String id = fixture.createContainer("postgres", Map.of(),
          "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17-alpine");
      var before = fixture.snapshot();
      assertThat(before.containers()).containsOnlyKeys(id);
      assertThat(before.volumes()).hasSize(1);
      assertThat(before.networkId()).matches("[a-f0-9]{64}");
      if (!scenario.equals("preparation_failure")) {
        fixture.command(Map.of(), Duration.ofSeconds(10), "start", id);
        boolean ready = false;
        for (int attempt = 0; attempt < 60; attempt++) {
          if (fixture.invoke(Map.of(), Duration.ofSeconds(5), "exec", id, "pg_isready", "-U", "postgres").exit() == 0) {
            ready = true;
            break;
          }
          Thread.sleep(250);
        }
        assertThat(ready).isTrue();
      }
      var preparation = new IllegalStateException("controlled failure after real allocation");
      var closing = new IllegalStateException("controlled context.close failure");
      GenericApplicationContext context = new GenericApplicationContext() {
        @Override public void close() {
          super.close();
          if (scenario.equals("context_close_failure")) throw closing;
        }
      };
      context.refresh();
      Throwable result = PreviewBackfillOwnedDockerResources.cleanupPreserving(
          scenario.equals("preparation_failure") ? preparation : null, context::close, fixture::close);
      if (scenario.equals("normal")) assertThat(result).isNull();
      else assertThat(result).isSameAs(scenario.equals("preparation_failure") ? preparation : closing);
      assertThat(context.isActive()).isFalse();
      fixture.verifyAbsent(); // Real Docker GETs after cleanup, including the exact anonymous volume name.
      assertThat(fixture.snapshot()).isEqualTo(before); // IDs are retained as evidence, not cleared to fake absence.
      System.out.println("PREVIEW_BACKFILL_CLEANUP_REAL=PASS scenario=" + scenario + " owner=" + before.owner()
          + " container=" + id + " volumes=" + before.volumes().keySet() + " network=" + before.networkId()
          + " evidence=" + fixture.evidence());
    } catch (Throwable failure) {
      testFailure = failure;
    } finally {
      Throwable result = PreviewBackfillOwnedDockerResources.cleanupPreserving(testFailure, fixture::close);
      if (result != null) throw result;
    }
  }

  private static PreviewBackfillOwnedDockerResources fixture(FakeDocker docker) throws Exception {
    return new PreviewBackfillOwnedDockerResources("topsv3-preview-cleanup-synthetic", docker);
  }
  private static void allocate(PreviewBackfillOwnedDockerResources fixture, boolean flyway) throws Exception {
    fixture.createNetwork();
    fixture.createContainer("postgres", Map.of(), "postgres:17-alpine");
    if (flyway) fixture.createContainer("flyway", Map.of(), "flyway/flyway:12.10.0");
  }

  /** In-memory Docker boundary for failure ordering; real resource tests above are separate. */
  private static final class FakeDocker implements PreviewBackfillOwnedDockerResources.Docker {
    static final String NETWORK = "b".repeat(64), PG = "c".repeat(64), FLYWAY = "d".repeat(64);
    static final String VOLUME = "a".repeat(64), HISTORICAL = "9".repeat(64);
    private final ObjectMapper json = new ObjectMapper();
    final List<List<String>> commands = new ArrayList<>();
    final Map<String, String> containers = new LinkedHashMap<>();
    String owner, networkName, containerOwnerOverride;
    boolean network, volume, historicalVolume = true, failPgRemoval, loseCreateResponse, ghostPgPresence;

    @Override public PreviewBackfillOwnedDockerResources.Result run(Map<String, String> environment, Duration timeout, String... input) throws Exception {
      List<String> args = List.of(input);
      commands.add(args);
      String last = args.get(args.size() - 1);
      if (args.get(0).equals("create")) {
        String role = args.stream().filter(arg -> arg.startsWith(PreviewBackfillOwnedDockerResources.ROLE_LABEL + "="))
            .findFirst().orElseThrow().split("=", 2)[1];
        String id = role.equals("postgres") ? PG : FLYWAY;
        containers.put(id, role);
        if (role.equals("postgres")) volume = true;
        if (loseCreateResponse) throw new PreviewBackfillOwnedDockerResources.AmbiguousCommand("controlled lost create response");
        return ok(id);
      }
      if (args.get(0).equals("rm")) {
        assertThat(args).contains("--volumes");
        if (last.equals(PG) && failPgRemoval) return new PreviewBackfillOwnedDockerResources.Result(9, "controlled");
        containers.remove(last);
        if (last.equals(PG)) volume = false;
        return ok(last);
      }
      String kind = args.get(0), verb = args.get(1);
      if (kind.equals("network")) {
        if (verb.equals("create")) {
          owner = args.stream().filter(arg -> arg.startsWith(PreviewBackfillOwnedDockerResources.OWNER_LABEL + "="))
              .findFirst().orElseThrow().split("=", 2)[1];
          networkName = last;
          network = true;
          return ok(NETWORK);
        }
        if (verb.equals("inspect")) return ok(NETWORK + "\n" + networkName + "\n"
            + json.writeValueAsString(Map.of(PreviewBackfillOwnedDockerResources.OWNER_LABEL, owner)));
        if (verb.equals("ls")) return ok(network ? NETWORK : "");
        if (verb.equals("rm")) {
          if (!containers.isEmpty()) return new PreviewBackfillOwnedDockerResources.Result(1, "network in use");
          network = false;
          return ok(NETWORK);
        }
      }
      if (kind.equals("container")) {
        if (verb.equals("inspect")) {
          String role = containers.get(last);
          var mounts = role.equals("postgres") ? List.of(Map.of("Type", "volume", "Name", VOLUME,
              "Destination", "/var/lib/postgresql/data")) : List.of();
          return ok(last + "\n/" + networkName.replaceFirst("-net$", "-" + role) + "\n"
              + json.writeValueAsString(Map.of(PreviewBackfillOwnedDockerResources.OWNER_LABEL,
                  containerOwnerOverride == null ? owner : containerOwnerOverride,
                  PreviewBackfillOwnedDockerResources.ROLE_LABEL, role)) + "\n" + json.writeValueAsString(mounts));
        }
        if (verb.equals("ls")) {
          String filter = option(args, "--filter");
          if (filter.startsWith("id=")) {
            String id = filter.substring(3);
            return ok(containers.containsKey(id) || (ghostPgPresence && id.equals(PG)) ? id : "");
          }
          if (filter.startsWith("volume=")) return ok(containers.containsKey(PG) ? PG : "");
          return ok(String.join("\n", containers.keySet()));
        }
      }
      if (kind.equals("volume")) {
        if (verb.equals("ls")) {
          String filter = option(args, "--filter");
          if (filter.isEmpty()) return ok(HISTORICAL + (volume ? "\n" + VOLUME : ""));
          return ok(volume && filter.equals("name=^" + VOLUME + "$") ? VOLUME : "");
        }
        if (verb.equals("inspect")) return ok(last + "\n2026-09-11T03:00:00Z\n"
            + json.writeValueAsString(Map.of("com.docker.volume.anonymous", "")));
        if (verb.equals("rm")) {
          assertThat(last).isEqualTo(VOLUME);
          volume = false;
          return ok(last);
        }
      }
      throw new AssertionError("Unmodelled Docker command " + args);
    }
    private String option(List<String> args, String key) { int index = args.indexOf(key); return index < 0 ? "" : args.get(index + 1); }
    private PreviewBackfillOwnedDockerResources.Result ok(String output) { return new PreviewBackfillOwnedDockerResources.Result(0, output); }
  }
}
