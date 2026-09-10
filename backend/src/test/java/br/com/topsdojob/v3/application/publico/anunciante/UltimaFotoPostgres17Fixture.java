package br.com.topsdojob.v3.application.publico.anunciante;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** PostgreSQL real, isolado e sem volumes persistentes ou servicos externos. */
final class UltimaFotoPostgres17Fixture implements AutoCloseable {

    private static final String OWNER_LABEL = "topsv3.ultima-foto.owner";
    private final String owner = UUID.randomUUID().toString();
    private final String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    private final Map<String, String> containers = new LinkedHashMap<>();
    private final Path logs;
    private String networkId;
    private String postgresId;
    private int port;
    private int sequence;
    private boolean ambiguous;
    private boolean allocationUnresolved;
    private boolean closed;

    private UltimaFotoPostgres17Fixture() throws IOException {
        Path target = Path.of("target").toAbsolutePath().normalize();
        Files.createDirectories(target);
        logs = Files.createTempDirectory(target, "ultima-foto-postgres17-");
    }

    static UltimaFotoPostgres17Fixture start() {
        UltimaFotoPostgres17Fixture fixture;
        try {
            fixture = new UltimaFotoPostgres17Fixture();
        } catch (IOException exception) {
            throw new IllegalStateException("nao foi possivel preparar evidencia da fixture", exception);
        }
        try {
            fixture.allocate();
            return fixture;
        } catch (Exception original) {
            try {
                fixture.close();
            } catch (Exception cleanup) {
                original.addSuppressed(cleanup);
            }
            throw new IllegalStateException("falha no PostgreSQL17 focal; causa e cleanup preservados", original);
        }
    }

    String jdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:" + port + "/ultima_foto";
    }

    String username() {
        return "ultima_foto_test";
    }

    String credential() {
        return credential;
    }

    private void allocate() throws Exception {
        for (String image : List.of("postgres:17.10-alpine", "flyway/flyway:12.10.0")) {
            command(Duration.ofSeconds(10), false, Map.of(), "image", "inspect", "--format", "{{.Id}}", image);
        }
        allocationUnresolved = true;
        networkId = id(command(Duration.ofSeconds(10), true, Map.of(), "network", "create",
                "--label", OWNER_LABEL + "=" + owner, "ultima-foto-" + owner));
        allocationUnresolved = false;
        postgresId = create("postgres", Map.of("POSTGRES_PASSWORD", credential),
                "--network", networkId, "--tmpfs", "/var/lib/postgresql/data:rw,nosuid,size=536870912",
                "-p", "127.0.0.1::5432", "-e", "POSTGRES_DB=ultima_foto",
                "-e", "POSTGRES_USER=" + username(), "-e", "POSTGRES_PASSWORD", "postgres:17.10-alpine");
        command(Duration.ofSeconds(10), true, Map.of(), "start", postgresId);
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        boolean ready = false;
        while (System.nanoTime() < deadline) {
            try {
                String result = command(remaining(deadline, 5), false, Map.of("PGPASSWORD", credential),
                        "exec", "-e", "PGPASSWORD", postgresId, "psql", "-X", "-w", "-h", "127.0.0.1",
                        "-U", username(), "-d", "ultima_foto", "-At", "-v", "ON_ERROR_STOP=1", "-c", "select 1");
                if (result.trim().equals("1") && System.nanoTime() < deadline) {
                    ready = true;
                    break;
                }
            } catch (CommandFailure exception) {
                // Somente indisponibilidade SQL durante inicializacao; timeout/ambiguidade nao e tolerado.
            }
            long pause = Math.min(200, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            if (pause > 0) Thread.sleep(pause);
        }
        if (!ready) throw new IllegalStateException("readiness PostgreSQL17 excedeu 90 segundos monotonicos");
        for (String action : List.of("migrate", "validate")) {
            String migration = create("flyway-" + action, Map.of("FLYWAY_PASSWORD", credential),
                    "--network", networkId, "-e", "FLYWAY_PASSWORD", "--mount",
                    "type=bind,source=" + Path.of("src/main/resources/db/migration").toAbsolutePath().normalize()
                            + ",target=/flyway/sql,readonly",
                    "flyway/flyway:12.10.0", "-url=jdbc:postgresql://ultima-foto-postgres-" + owner + ":5432/ultima_foto",
                    "-user=" + username(), "-locations=filesystem:/flyway/sql", action);
            command(Duration.ofSeconds(120), true, Map.of(), "start", "--attach", migration);
            removeContainer(migration, System.nanoTime() + Duration.ofSeconds(30).toNanos());
        }
        String binding = command(Duration.ofSeconds(10), false, Map.of(), "port", postgresId, "5432/tcp").trim();
        if (!binding.matches("127\\.0\\.0\\.1:[0-9]+")) {
            throw new IllegalStateException("binding PostgreSQL inesperado");
        }
        port = Integer.parseInt(binding.substring(binding.lastIndexOf(':') + 1));
        System.out.println("ULTIMA_FOTO_FIXTURE_READY owner=" + owner + " postgres=" + postgresId
                + " network=" + networkId + " volumes=0");
    }

    private String create(String role, Map<String, String> environment, String... arguments) throws Exception {
        List<String> args = new ArrayList<>(List.of("create", "--pull=never", "--name", "ultima-foto-" + role + "-" + owner,
                "--label", OWNER_LABEL + "=" + owner));
        args.addAll(List.of(arguments));
        allocationUnresolved = true;
        String container = id(command(Duration.ofSeconds(20), true, environment, args.toArray(String[]::new)));
        containers.put(container, role);
        allocationUnresolved = false;
        verifyContainer(container, System.nanoTime() + Duration.ofSeconds(15).toNanos());
        return container;
    }

    @Override
    public synchronized void close() throws Exception {
        if (closed) return;
        if (ambiguous || allocationUnresolved) {
            throw new IllegalStateException("cleanup bloqueado: comando Docker ambiguo; executor externo deve preservar erro");
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        for (String container : containers.keySet()) removeContainer(container, deadline);
        if (networkId != null) {
            String present = command(remaining(deadline, 10), false, Map.of(), "network", "ls", "--no-trunc",
                    "--filter", "id=" + networkId, "--format", "{{.ID}}").trim();
            if (!present.isEmpty()) {
                String identity = command(remaining(deadline, 10), false, Map.of(), "network", "inspect", "--format",
                        "{{.Id}}|{{index .Labels \"" + OWNER_LABEL + "\"}}", networkId).trim();
                if (!identity.equals(networkId + "|" + owner)) throw new IllegalStateException("ownership da rede divergente");
                command(remaining(deadline, 10), true, Map.of(), "network", "rm", networkId);
            }
            if (!command(remaining(deadline, 10), false, Map.of(), "network", "ls", "--no-trunc", "--filter",
                    "id=" + networkId, "--format", "{{.ID}}").isBlank()) {
                throw new IllegalStateException("rede propria permaneceu apos cleanup");
            }
        }
        closed = true;
        System.out.println("ULTIMA_FOTO_FIXTURE_CLEANUP=PASS owner=" + owner + " containers=" + containers.size()
                + " network=" + networkId + " volumes=0");
    }

    private void removeContainer(String container, long deadline) throws Exception {
        String present = command(remaining(deadline, 10), false, Map.of(), "container", "ls", "--all", "--no-trunc",
                "--filter", "id=" + container, "--format", "{{.ID}}").trim();
        if (!present.isEmpty()) {
            if (!present.equals(container)) throw new IllegalStateException("identidade do container divergente");
            verifyContainer(container, deadline);
            command(remaining(deadline, 10), true, Map.of(), "rm", "--force", container);
        }
        if (!command(remaining(deadline, 10), false, Map.of(), "container", "ls", "--all", "--no-trunc",
                "--filter", "id=" + container, "--format", "{{.ID}}").isBlank()) {
            throw new IllegalStateException("container proprio permaneceu apos cleanup");
        }
    }

    private void verifyContainer(String container, long deadline) throws Exception {
        String identity = command(remaining(deadline, 10), false, Map.of(), "container", "inspect", "--format",
                "{{.Id}}|{{index .Config.Labels \"" + OWNER_LABEL + "\"}}|{{range .Mounts}}{{if eq .Type \"volume\"}}{{.Name}}{{end}}{{end}}",
                container).trim();
        if (!identity.equals(container + "|" + owner + "|")) {
            throw new IllegalStateException("ownership ou ausencia de volumes da fixture nao comprovados");
        }
    }

    private String command(Duration timeout, boolean mutation, Map<String, String> environment, String... args)
            throws Exception {
        if (mutation && ambiguous) throw new IllegalStateException("mutacao apos ambiguidade recusada");
        Path log = logs.resolve(String.format("%03d-%s.log", ++sequence, args[0]));
        List<String> command = new ArrayList<>(List.of("docker"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        String outcome = "STARTED";
        try {
            if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                outcome = "TIMEOUT";
                ambiguous |= mutation;
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) ambiguous = true;
                }
                throw new IllegalStateException("deadline Docker excedido; evidencia " + log.getFileName());
            }
            outcome = Integer.toString(process.exitValue());
        } catch (InterruptedException exception) {
            outcome = "INTERRUPTED";
            ambiguous = true;
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            Files.writeString(log.resolveSibling(log.getFileName() + ".command.json"),
                    new ObjectMapper().writeValueAsString(Map.of(
                            "executable", "docker", "arguments", List.of(args),
                            "timeoutMs", timeout.toMillis(), "outcome", outcome,
                            "mutation", mutation, "environmentValuesOmitted", true,
                            "allocationUnresolved", allocationUnresolved, "ambiguous", ambiguous)),
                    StandardCharsets.UTF_8);
        }
        if (process.exitValue() != 0) throw new CommandFailure(process.exitValue(), log.getFileName().toString());
        return Files.readString(log, StandardCharsets.UTF_8);
    }

    private static Duration remaining(long deadline, int seconds) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) throw new IllegalStateException("deadline total da fixture excedido");
        return Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(seconds).toNanos()));
    }

    private static String id(String output) {
        String value = output.trim();
        if (!value.matches("[a-f0-9]{64}")) throw new IllegalStateException("Docker nao devolveu ID completo");
        return value;
    }

    private static final class CommandFailure extends Exception {
        private CommandFailure(int exit, String evidence) {
            super("Docker exit=" + exit + "; evidencia " + evidence);
        }
    }
}
