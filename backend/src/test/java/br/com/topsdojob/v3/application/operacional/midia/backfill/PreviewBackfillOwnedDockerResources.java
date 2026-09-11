package br.com.topsdojob.v3.application.operacional.midia.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Test-only ownership ledger; never discovers or deletes historical unowned volumes. */
final class PreviewBackfillOwnedDockerResources implements AutoCloseable {
  static final String OWNER_LABEL = "topsv3.preview-backfill.owner";
  static final String ROLE_LABEL = "topsv3.preview-backfill.role";
  private static final ObjectMapper JSON = new ObjectMapper();
  private final String owner = UUID.randomUUID().toString();
  private final String prefix;
  private final Docker docker;
  private final Path evidence;
  private final Set<String> volumesBefore = new LinkedHashSet<>();
  private final Set<String> intendedRoles = new LinkedHashSet<>();
  private final Map<String, String> containers = new LinkedHashMap<>();
  private final Map<String, Volume> volumes = new LinkedHashMap<>();
  private final List<Throwable> ambiguities = new ArrayList<>();
  private String networkId;
  private boolean allocationStarted;
  private boolean closed;
  private int sequence;

  PreviewBackfillOwnedDockerResources(String prefix) throws Exception {
    this(prefix, new ProcessDocker());
  }

  PreviewBackfillOwnedDockerResources(String prefix, Docker docker) throws Exception {
    if (!prefix.matches("topsv3-preview-[a-z0-9-]+")) throw new IllegalArgumentException("invalid fixture prefix");
    this.prefix = prefix;
    this.docker = docker;
    Path target = Path.of("target").toAbsolutePath().normalize();
    Files.createDirectories(target);
    evidence = Files.createTempDirectory(target, "preview-backfill-cleanup-");
    if (docker instanceof ProcessDocker processDocker) {
      processDocker.evidence = Files.createDirectory(evidence.resolve("commands"));
    }
  }

  String owner() { return owner; }
  Path evidence() { return evidence; }
  String name(String role) { return prefix + "-" + role; }

  void createNetwork() throws Exception {
    if (allocationStarted) throw new IllegalStateException("fixture allocation already started");
    volumesBefore.addAll(names("volume", "ls", "--quiet"));
    allocationStarted = true; // Recorded before invoking the daemon, including a lost response.
    networkId = fullId(command(Map.of(), Duration.ofSeconds(10),
        "network", "create", "--label", OWNER_LABEL + "=" + owner, name("net")));
    verifyNetwork();
    receipt("network-created", null);
  }

  String createContainer(String role, Map<String, String> environment, String... arguments) throws Exception {
    if (!Set.of("postgres", "flyway").contains(role) || !intendedRoles.add(role)) {
      throw new IllegalArgumentException("unexpected or repeated fixture role");
    }
    List<String> args = new ArrayList<>(List.of("create", "--pull=never", "--name", name(role),
        "--label", OWNER_LABEL + "=" + owner, "--label", ROLE_LABEL + "=" + role,
        "--network", networkId));
    args.addAll(List.of(arguments));
    String id = fullId(command(environment, Duration.ofSeconds(20), args.toArray(String[]::new)));
    containers.put(id, role);
    captureVolumes(id, verifyContainer(id));
    receipt("container-created-before-start", null);
    return id;
  }

  String command(Map<String, String> environment, Duration timeout, String... arguments) throws Exception {
    Result result = invoke(environment, timeout, arguments);
    if (result.exit() != 0) throw new IllegalStateException("Docker " + arguments[0] + " exit=" + result.exit());
    return result.output();
  }

  Result invoke(Map<String, String> environment, Duration timeout, String... arguments) throws Exception {
    try {
      return docker.run(environment, timeout, arguments);
    } catch (AmbiguousCommand failure) {
      ambiguities.add(failure);
      throw failure;
    }
  }

  Snapshot snapshot() { return new Snapshot(owner, networkId, Map.copyOf(containers), Map.copyOf(volumes)); }

  @Override
  public void close() throws Exception {
    if (closed) return;
    List<Throwable> failures = new ArrayList<>(ambiguities);
    attempt(failures, this::discoverOwnedResources);
    attempt(failures, () -> receipt("before-cleanup", null));
    // Consumers first, but a failure never prevents the independent remaining attempts.
    List<String> ids = new ArrayList<>(containers.keySet());
    ids.sort((a, b) -> Boolean.compare("postgres".equals(containers.get(a)), "postgres".equals(containers.get(b))));
    for (String id : ids) attempt(failures, () -> removeContainer(id));
    for (Volume volume : new ArrayList<>(volumes.values())) attempt(failures, () -> removeVolume(volume));
    attempt(failures, this::removeNetwork);
    attempt(failures, this::verifyAbsent);
    for (Throwable ambiguity : ambiguities) if (!failures.contains(ambiguity)) failures.add(ambiguity);
    attempt(failures, () -> receipt("after-cleanup", failures.isEmpty() ? "ABSENT" : "INCOMPLETE"));
    if (!failures.isEmpty()) {
      Throwable first = cleanupPreserving(null, failures.stream().<Action>map(error -> () -> { throw error; }).toArray(Action[]::new));
      if (first instanceof Exception exception) throw exception;
      if (first instanceof Error error) throw error;
      throw new IllegalStateException("fixture cleanup failed", first);
    }
    closed = true;
    System.out.println("PREVIEW_BACKFILL_FIXTURE_CLEANUP=PASS owner=" + owner
        + " containers=" + containers.keySet() + " volumes=" + volumes.keySet() + " network=" + networkId);
  }

  void verifyAbsent() throws Exception {
    for (String id : containers.keySet()) if (containerPresent(id)) throw new IllegalStateException("owned container remains: " + id);
    for (String volume : volumes.keySet()) if (volumePresent(volume)) throw new IllegalStateException("owned volume remains: " + volume);
    if (networkId != null && networkPresent(networkId)) throw new IllegalStateException("owned network remains: " + networkId);
    if (!names("container", "ls", "--all", "--quiet", "--no-trunc", "--filter", "label=" + OWNER_LABEL + "=" + owner).isEmpty()
        || !names("network", "ls", "--quiet", "--no-trunc", "--filter", "label=" + OWNER_LABEL + "=" + owner).isEmpty()) {
      throw new IllegalStateException("unexpected resources with fixture ownership remain");
    }
  }

  private void discoverOwnedResources() throws Exception {
    if (!allocationStarted) return;
    Set<String> networks = names("network", "ls", "--quiet", "--no-trunc", "--filter", "label=" + OWNER_LABEL + "=" + owner);
    if (networks.size() > 1) throw new IllegalStateException("multiple networks for this fixture owner");
    for (String id : networks) {
      fullId(id);
      if (networkId != null && !networkId.equals(id)) throw new IllegalStateException("network ID changed");
      networkId = id;
      verifyNetwork();
    }
    List<Throwable> failures = new ArrayList<>();
    for (String id : names("container", "ls", "--all", "--quiet", "--no-trunc", "--filter", "label=" + OWNER_LABEL + "=" + owner)) {
      attempt(failures, () -> {
        fullId(id);
        JsonNode identity = containerIdentity(id);
        String role = identity.path("role").asText();
        if (!intendedRoles.contains(role)) throw new IllegalStateException("unexpected fixture role");
        String previous = containers.putIfAbsent(id, role);
        if (previous != null && !previous.equals(role)) throw new IllegalStateException("container role changed");
        captureVolumes(id, verifyContainer(id));
      });
    }
    if (!failures.isEmpty()) {
      Throwable failure = failures.get(0);
      for (int i = 1; i < failures.size(); i++) if (failure != failures.get(i)) failure.addSuppressed(failures.get(i));
      if (failure instanceof Exception exception) throw exception;
      throw new IllegalStateException("owned resource discovery failed", failure);
    }
  }

  private JsonNode containerIdentity(String id) throws Exception {
    // No embedded quotes in Go templates: Windows ProcessBuilder otherwise strips them.
    String[] lines = command(Map.of(), Duration.ofSeconds(10), "container", "inspect", "--format",
        "{{.Id}}\n{{.Name}}\n{{json .Config.Labels}}\n{{json .Mounts}}", id).strip().split("\\R", 4);
    if (lines.length != 4) throw new IllegalStateException("incomplete container identity");
    JsonNode labels = JSON.readTree(lines[2]);
    var identity = JSON.createObjectNode();
    identity.put("id", lines[0]).put("name", lines[1]);
    identity.set("owner", labels.path(OWNER_LABEL));
    identity.set("role", labels.path(ROLE_LABEL));
    identity.set("mounts", JSON.readTree(lines[3]));
    return identity;
  }

  private JsonNode verifyContainer(String id) throws Exception {
    JsonNode identity = containerIdentity(id);
    String role = containers.get(id);
    if (role == null || !id.equals(identity.path("id").asText()) || !owner.equals(identity.path("owner").asText())
        || !role.equals(identity.path("role").asText()) || !("/" + name(role)).equals(identity.path("name").asText())) {
      throw new IllegalStateException("container identity/ownership mismatch: " + id);
    }
    return identity;
  }

  private void verifyNetwork() throws Exception {
    String[] lines = command(Map.of(), Duration.ofSeconds(10), "network", "inspect", "--format",
        "{{.Id}}\n{{.Name}}\n{{json .Labels}}", networkId).strip().split("\\R", 3);
    if (lines.length != 3 || !networkId.equals(lines[0]) || !name("net").equals(lines[1])
        || !owner.equals(JSON.readTree(lines[2]).path(OWNER_LABEL).asText())) {
      throw new IllegalStateException("network identity/ownership mismatch");
    }
  }

  private void captureVolumes(String container, JsonNode identity) throws Exception {
    JsonNode mounts = identity.path("mounts");
    if (!mounts.isArray()) throw new IllegalStateException("missing container mounts");
    for (JsonNode mount : mounts) {
      if (!"volume".equals(mount.path("Type").asText())) continue;
      String volume = mount.path("Name").asText();
      if (!volume.matches("[a-f0-9]{64}") || volumesBefore.contains(volume)) throw new IllegalStateException("unattributed volume; preserve it");
      JsonNode actual = volumeIdentity(volume);
      if (!actual.path("Labels").has("com.docker.volume.anonymous") || actual.path("CreatedAt").asText().isBlank()) {
        throw new IllegalStateException("anonymous volume identity unproven");
      }
      Volume expected = new Volume(volume, actual.path("CreatedAt").asText(), container);
      Volume previous = volumes.putIfAbsent(volume, expected);
      if (previous != null && !previous.equals(expected)) throw new IllegalStateException("anonymous volume identity changed");
    }
  }

  private JsonNode volumeIdentity(String name) throws Exception {
    String[] lines = command(Map.of(), Duration.ofSeconds(10), "volume", "inspect", "--format",
        "{{.Name}}\n{{.CreatedAt}}\n{{json .Labels}}", name).strip().split("\\R", 3);
    if (lines.length != 3) throw new IllegalStateException("incomplete volume identity");
    var identity = JSON.createObjectNode();
    identity.put("Name", lines[0]).put("CreatedAt", lines[1]);
    identity.set("Labels", JSON.readTree(lines[2]));
    if (!name.equals(identity.path("Name").asText())) throw new IllegalStateException("volume name mismatch");
    return identity;
  }

  private void removeContainer(String id) throws Exception {
    if (!containerPresent(id)) return;
    captureVolumes(id, verifyContainer(id));
    command(Map.of(), Duration.ofSeconds(15), "rm", "--force", "--volumes", id);
    if (containerPresent(id)) throw new IllegalStateException("container remains after rm: " + id);
  }

  private void removeVolume(Volume expected) throws Exception {
    if (!volumePresent(expected.name())) return;
    JsonNode actual = volumeIdentity(expected.name());
    if (!expected.createdAt().equals(actual.path("CreatedAt").asText()) || !actual.path("Labels").has("com.docker.volume.anonymous")) {
      throw new IllegalStateException("volume changed; removal refused");
    }
    if (!names("container", "ls", "--all", "--quiet", "--filter", "volume=" + expected.name()).isEmpty()) {
      throw new IllegalStateException("volume still referenced; removal refused");
    }
    command(Map.of(), Duration.ofSeconds(10), "volume", "rm", expected.name());
    if (volumePresent(expected.name())) throw new IllegalStateException("volume remains after rm");
  }

  private void removeNetwork() throws Exception {
    if (networkId == null || !networkPresent(networkId)) return;
    verifyNetwork();
    command(Map.of(), Duration.ofSeconds(10), "network", "rm", networkId);
    if (networkPresent(networkId)) throw new IllegalStateException("network remains after rm");
  }

  private boolean containerPresent(String id) throws Exception {
    return exactPresence(id, names("container", "ls", "--all", "--no-trunc", "--filter", "id=" + id, "--format", "{{.ID}}"));
  }
  private boolean networkPresent(String id) throws Exception {
    return exactPresence(id, names("network", "ls", "--no-trunc", "--filter", "id=" + id, "--format", "{{.ID}}"));
  }
  private boolean volumePresent(String name) throws Exception {
    return exactPresence(name, names("volume", "ls", "--filter", "name=^" + name + "$", "--format", "{{.Name}}"));
  }
  private boolean exactPresence(String expected, Set<String> actual) {
    if (!actual.isEmpty() && !actual.equals(Set.of(expected))) throw new IllegalStateException("ambiguous resource presence");
    return !actual.isEmpty();
  }
  private Set<String> names(String... arguments) throws Exception {
    String output = command(Map.of(), Duration.ofSeconds(10), arguments);
    return new LinkedHashSet<>(output.lines().map(String::trim).filter(value -> !value.isEmpty()).toList());
  }
  private static String fullId(String value) {
    String id = value.trim();
    if (!id.matches("[a-f0-9]{64}")) throw new IllegalStateException("Docker did not return a full ID");
    return id;
  }
  private void receipt(String stage, String outcome) throws Exception {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("stage", stage);
    record.put("snapshot", snapshot());
    record.put("outcome", outcome);
    record.put("ambiguities", ambiguities.size());
    Files.writeString(evidence.resolve(String.format("%03d-%s.json", ++sequence, stage)), JSON.writeValueAsString(record));
    System.out.println("PREVIEW_BACKFILL_FIXTURE_RESOURCES stage=" + stage + " owner=" + owner
        + " containers=" + containers.keySet() + " volumes=" + volumes.keySet() + " network=" + networkId);
  }

  static Throwable cleanupPreserving(Throwable primary, Action... actions) {
    Throwable first = primary;
    for (Action action : actions) {
      try { action.run(); }
      catch (Throwable additional) {
        if (first == null) first = additional;
        else if (first != additional && !Arrays.asList(first.getSuppressed()).contains(additional)) first.addSuppressed(additional);
      }
    }
    return first;
  }
  private static void attempt(List<Throwable> failures, Action action) {
    try { action.run(); } catch (Throwable failure) { failures.add(failure); }
  }
  @FunctionalInterface interface Action { void run() throws Throwable; }
  @FunctionalInterface interface Docker { Result run(Map<String, String> environment, Duration timeout, String... arguments) throws Exception; }
  record Result(int exit, String output) { }
  record Volume(String name, String createdAt, String containerId) { }
  record Snapshot(String owner, String networkId, Map<String, String> containers, Map<String, Volume> volumes) { }
  static final class AmbiguousCommand extends Exception {
    AmbiguousCommand(String message) { super(message); }
  }

  private static final class ProcessDocker implements Docker {
    private Path evidence;
    private int sequence;
    @Override
    public Result run(Map<String, String> environment, Duration timeout, String... arguments) throws Exception {
      Path output = evidence.resolve(String.format("%03d-%s.log", ++sequence, arguments[0]));
      List<String> args = new ArrayList<>(List.of("docker"));
      args.addAll(List.of(arguments));
      ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.toFile());
      builder.environment().putAll(environment);
      Process process = null;
      Throwable primary = null;
      try {
        process = builder.start();
        process.getOutputStream().close();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
          process.destroy();
          if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
          throw new AmbiguousCommand("Docker " + arguments[0] + " timed out; daemon outcome remains unknown");
        }
        return new Result(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
      } catch (InterruptedException interrupted) {
        if (process != null) process.destroyForcibly();
        Thread.currentThread().interrupt();
        AmbiguousCommand failure = new AmbiguousCommand("Docker collection interrupted; daemon outcome remains unknown");
        failure.addSuppressed(interrupted);
        primary = failure;
        throw failure;
      } catch (Exception | Error failure) {
        primary = failure;
        throw failure;
      } finally {
        try {
          Files.writeString(output.resolveSibling(output.getFileName() + ".json"), JSON.writeValueAsString(Map.of(
              "operation", arguments[0], "timeoutMs", timeout.toMillis(), "environmentValuesOmitted", true,
              "outcome", primary == null ? "EXIT_" + process.exitValue() : primary.getClass().getSimpleName())));
        }
        catch (Exception cleanup) {
          if (primary != null) primary.addSuppressed(cleanup);
          else throw cleanup;
        }
      }
    }
  }
}
