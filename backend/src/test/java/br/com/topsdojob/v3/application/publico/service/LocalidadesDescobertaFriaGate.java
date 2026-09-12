package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Checkout-only controller. Each container starts a new JVM; failures are test failures. */
final class LocalidadesDescobertaFriaGate {
    private static final String IMAGE = "eclipse-temurin:17.0.13_11-jre";

    static void executar(HikariDataSource pool) throws Exception {
        long deadline = System.nanoTime() + Duration.ofMinutes(8).toNanos();
        assertThat(pool.getJdbcUrl()).matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/topsv3_localidades\\?ApplicationName=localidades-it");
        String owner = UUID.randomUUID().toString();
        Path evidence = Path.of("target", "localidades-cold", owner).toAbsolutePath().normalize();
        Files.createDirectories(evidence);
        // Preparation is bounded and precedes every measured child JVM, including on a new host.
        prepararImagem(evidence);
        Path inputs = Files.createDirectory(evidence.resolve("inputs"));
        // All inputs come from this checkout's current Surefire classpath, not a historical JAR.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        int index = 0;
        List<String> childClasspath = new ArrayList<>();
        String agent = null;
        for (String item : classpath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            Path source = Path.of(item).toAbsolutePath().normalize();
            if (!Files.exists(source)) throw new IllegalStateException("Classpath input missing: " + source);
            String name = String.format(java.util.Locale.ROOT, "%03d", index++)
                    + (Files.isDirectory(source) ? "" : ".jar");
            Path destination = inputs.resolve(name);
            copy(source, destination);
            childClasspath.add("/tmp/cold/" + name);
            if (source.getFileName().toString().startsWith("byte-buddy-agent-")) agent = "/tmp/cold/" + name;
        }
        if (agent == null) throw new IllegalStateException("Existing Mockito agent missing");
        boolean desktop = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        String jdbc = desktop ? pool.getJdbcUrl().replace("127.0.0.1", "host.docker.internal") : pool.getJdbcUrl();
        Map<String, String> environment = Map.of(
                "TOPS_COLD_JDBC", jdbc, "SPRING_DATASOURCE_PASSWORD", pool.getPassword(),
                "LOCALIDADES_POSTGRES17_ENABLED", "true");
        List<String> failures = new ArrayList<>();
        for (int jvm = 1; jvm <= 3; jvm++) {
            String name = "tops-localidades-cold-" + owner + "-" + jvm;
            Path log = evidence.resolve("jvm-" + jvm + ".log");
            int rounds = jvm == 3 ? 3 : 1;
            var command = new ArrayList<>(List.of("docker", "run", "--pull=never",
                    "--name", name, "--label", "topsv3.localidades.cold.owner=" + owner,
                    "--cpuset-cpus", "0,1", "--memory", "8325693440"));
            if (!desktop) command.addAll(List.of("--network", "host"));
            command.addAll(List.of("-e", "TOPS_COLD_JDBC", "-e", "SPRING_DATASOURCE_PASSWORD",
                    "-e", "LOCALIDADES_POSTGRES17_ENABLED", "--mount",
                    "type=bind,source=" + inputs + ",target=/inputs,readonly",
                    "--entrypoint", "sh", IMAGE, "-c",
                    "mkdir /tmp/cold && cp -R /inputs/. /tmp/cold/ && exec java -Xmx2g -javaagent:"
                            + agent + " -cp '" + String.join(":", childClasspath) + "' "
                            + LocalidadesDescobertaFriaProcesso.class.getName() + " " + rounds));
            try {
                int exit = run(command, environment, log,
                        Math.min(120, Math.max(1, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()))));
                String output = Files.readString(log);
                System.out.println(output);
                // Missing/incomplete output cannot be rescued by exit=0.
                long results = output.lines().filter(line -> line.startsWith("COLD_RESULT ")).count();
                long passed = output.lines().filter(line -> line.startsWith("COLD_RESULT ") && line.contains("result=PASS")).count();
                if (exit != 0 || results != rounds || passed != rounds || !output.contains("COLD_CLEANUP=PASS")) {
                    failures.add("JVM " + jvm + " exit=" + exit + " results=" + results + " passed=" + passed);
                }
            } catch (Exception error) {
                failures.add("JVM " + jvm + " " + error.getClass().getSimpleName());
                if (Files.exists(log)) System.out.println(Files.readString(log));
            } finally {
                // Do not use --rm: inspect exact ownership even after a timeout/failed CLI.
                try {
                    Path inspect = evidence.resolve("inspect-" + jvm + ".log");
                    int found = run(List.of("docker", "container", "inspect", "--format",
                            "{{json .Config.Labels}}", name), Map.of(), inspect, 15);
                    if (found == 0) {
                        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(Files.readString(inspect)).path("topsv3.localidades.cold.owner").asText())
                                .isEqualTo(owner);
                        assertThat(run(List.of("docker", "rm", "-f", "-v", name), Map.of(),
                                evidence.resolve("cleanup-" + jvm + ".log"), 15)).isZero();
                        Path removed = evidence.resolve("removed-" + jvm + ".log");
                        assertThat(run(List.of("docker", "container", "inspect", name), Map.of(), removed, 15))
                                .isNotZero();
                        assertThat(Files.readString(removed).toLowerCase(java.util.Locale.ROOT)).contains("no such");
                    } else {
                        assertThat(Files.readString(inspect).toLowerCase(java.util.Locale.ROOT)).contains("no such");
                        failures.add("JVM " + jvm + " container absent");
                    }
                } catch (Exception | AssertionError error) {
                    failures.add("JVM " + jvm + " cleanup failed: " + error.getClass().getSimpleName());
                    error.printStackTrace(System.out);
                }
            }
            if (System.nanoTime() >= deadline) {
                failures.add("Overall cold gate deadline exceeded");
                break;
            }
        }
        System.out.println("COLD_GATE_EVIDENCE=" + evidence);
        assertThat(failures).as("All three first discoveries and both reused calls must pass").isEmpty();
    }

    private static void prepararImagem(Path evidence) throws Exception {
        Path inspect = evidence.resolve("image-inspect.log");
        boolean cached = run(List.of("docker", "image", "inspect", "--format",
                "{{json .RepoTags}}", IMAGE), Map.of(), inspect, 15) == 0;
        if (!cached) {
            String failure = Files.readString(inspect).toLowerCase(java.util.Locale.ROOT);
            if (!failure.contains("no such image") && !failure.contains("not found")) {
                throw new IllegalStateException("Cannot inspect exact cold JVM image; see " + inspect);
            }
            Path pull = evidence.resolve("image-pull.log");
            if (run(List.of("docker", "pull", IMAGE), Map.of(), pull, 120) != 0) {
                throw new IllegalStateException("Cannot prepare exact cold JVM image; see " + pull);
            }
            if (run(List.of("docker", "image", "inspect", "--format", "{{json .RepoTags}}", IMAGE),
                    Map.of(), inspect, 15) != 0) {
                throw new IllegalStateException("Exact cold JVM image missing after pull; see " + inspect);
            }
        }
        var tags = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(inspect));
        assertThat(tags.isArray()).isTrue();
        boolean exact = false;
        for (var tag : tags) exact |= IMAGE.equals(tag.asText());
        assertThat(exact).as("Declared exact Java image is locally prepared").isTrue();
        System.out.printf("COLD_IMAGE image=%s cached=%s preparationOutsideMeasurement=true%n", IMAGE, cached);
    }

    private static int run(List<String> args, Map<String, String> environment, Path log, long seconds)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new IllegalStateException("Subprocess deadline exceeded");
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalStateException("CLI did not terminate");
            }
        }
    }

    private static void copy(Path source, Path destination) throws Exception {
        if (!Files.isDirectory(source)) { Files.copy(source, destination); return; }
        try (var paths = Files.walk(source)) {
            for (Path item : paths.toList()) {
                if (Files.isSymbolicLink(item)) throw new IllegalStateException("Unexpected classpath symlink");
                Path target = destination.resolve(source.relativize(item));
                if (Files.isDirectory(item)) Files.createDirectories(target);
                else Files.copy(item, target);
            }
        }
    }
}
