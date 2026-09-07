package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.TestContextManager;
import org.springframework.web.server.ResponseStatusException;

/** Child JVM entry point. Fixture seeding never calls discovery or the runtime derivation. */
public final class LocalidadesDescobertaFriaProcesso {
    static boolean configurarJdbc(ConfigurableApplicationContext context) {
        String url = System.getenv("TOPS_COLD_JDBC");
        if (url == null) return false;
        assertThat(url).matches("jdbc:postgresql://(127\\.0\\.0\\.1|host\\.docker\\.internal):[0-9]+/topsv3_localidades\\?ApplicationName=localidades-it");
        String password = System.getenv("TOPS_COLD_PASSWORD");
        assertThat(password != null && !password.isBlank()).isTrue();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-cold-pg17",
                Map.ofEntries(
                        Map.entry("spring.datasource.url", url),
                        Map.entry("spring.datasource.username", "topsv3test"),
                        Map.entry("spring.datasource.password", password),
                        Map.entry("spring.datasource.hikari.maximum-pool-size", "5"),
                        Map.entry("spring.datasource.hikari.minimum-idle", "5"),
                        Map.entry("spring.datasource.hikari.connection-timeout", "30000"),
                        Map.entry("spring.flyway.enabled", "false"),
                        Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                        Map.entry("spring.jpa.properties.hibernate.generate_statistics", "true"),
                        Map.entry("logging.level.org.hibernate.stat", "OFF"),
                        Map.entry("logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener", "OFF"))));
        return true;
    }

    public static void main(String[] args) throws Exception {
        assertThat("true".equals(System.getenv("LOCALIDADES_POSTGRES17_ENABLED"))).isTrue();
        assertThat(System.getenv("TOPS_COLD_JDBC")).isNotNull();
        assertThat(args).hasSize(1);
        int rounds = Integer.parseInt(args[0]);
        assertThat(rounds).isIn(1, 3);
        assertThat(System.getProperty("java.version")).isEqualTo("17.0.13");
        assertThat(Runtime.getRuntime().availableProcessors()).isEqualTo(2);
        assertThat(cgroup("cpuset.cpus.effective")).isIn("0-1", "0,1");
        assertThat(Runtime.getRuntime().maxMemory()).isBetween(2_000_000_000L, 2_150_000_000L);
        System.out.printf("COLD_CONFIG java=%s cpus=2 cpuset=%s maxHeap=%d cpuMax=%s warmup=false%n",
                System.getProperty("java.version"), cgroup("cpuset.cpus.effective"),
                Runtime.getRuntime().maxMemory(), cgroup("cpu.max"));
        var fixture = new LocalidadesConsultaCapacidadePostgres17IntegrationTest();
        var manager = new TestContextManager(fixture.getClass());
        ConfigurableApplicationContext context = null;
        boolean prepared = false;
        int failures = 0;
        try {
            manager.beforeTestClass();
            manager.prepareTestInstance(fixture);
            context = (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext();
            fixture.prepare();
            prepared = true;
            Object inventory = call(fixture, "seed", 715, 975);
            Object remote = field(fixture, "REMOTE");
            call(remote, "configure", call(inventory, "keys"), 1358, new long[]{1839, 837});
            System.out.printf("COLD_DATASET idsSha256=%s keysSha256=%s ads=715 previews=975 objects=1358 bytes=401907 delays=1839,837%n",
                    fingerprint(call(inventory, "ads")), fingerprint(call(inventory, "keys")));
            var service = (LocalidadePublicaConsultaService) field(fixture, "service");
            var em = (jakarta.persistence.EntityManager) field(fixture, "em");
            var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            var verifier = (R2VerificacaoAgrupadaPreviews) field(fixture, "verifier");
            var derivation = (MidiaRestritaDerivacaoService) field(fixture, "derivation");
            // Prove the observer recognizes both overload names; no real derivation/discovery is called.
            var observerProbe = org.mockito.Mockito.mock(MidiaRestritaDerivacaoService.class);
            observerProbe.resolverPreviewPublica(null);
            observerProbe.resolverPreviewPublicaLeitura(null, null);
            assertThat(calls(observerProbe, "resolverPreview")).isEqualTo(2);
            Object reference = null;
            for (int round = 1; round <= rounds; round++) {
                Object before = call(fixture, "metrics");
                long entities = stats.getEntityLoadCount();
                long sqlStart = stats.getPrepareStatementCount();
                long previewCalls = calls(derivation, "resolverPreview");
                long verificationCalls = calls(verifier, "verificar");
                long cpu = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
                String throttle = cgroup("cpu.stat");
                long start = System.nanoTime();
                Object response = null;
                Throwable error = null;
                try { response = service.descobrir(); } catch (Throwable failure) { error = failure; }
                double elapsed = (System.nanoTime() - start) / 1e6;
                long cpuDelta = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime() - cpu;
                call(fixture, "drained");
                call(fixture, "evidence", "cold_process_round_" + round, start, elapsed, before);
                try {
                    if (error != null) throw error;
                    call(fixture, "assertComplete", response, call(inventory, "ads"));
                    if (reference != null) assertThat(response).isEqualTo(reference);
                    reference = response;
                    assertThat(elapsed).isLessThan(3500.0);
                    Object after = call(fixture, "metrics");
                    assertThat((long) call(after, "transactions") - (long) call(before, "transactions")).isEqualTo(1);
                    assertThat((int) call(after, "lists") - (int) call(before, "lists")).isZero();
                    assertThat((int) call(after, "heads") - (int) call(before, "heads")).isZero();
                    assertThat((long) call(after, "bytes") - (long) call(before, "bytes")).isZero();
                    assertThat(((AtomicInteger) field(remote, "maxActive")).get()).isZero();
                    assertThat(calls(derivation, "resolverPreview") - previewCalls).isZero();
                    assertThat(calls(verifier, "verificar") - verificationCalls).isZero();
                    assertThat(stats.getPrepareStatementCount() - sqlStart)
                            .as("Definitive snapshot executes SQL and Hibernate measurement is active").isPositive();
                } catch (Throwable invalid) { error = invalid; failures++; }
                System.out.printf(java.util.Locale.ROOT,
                        "COLD_RESULT round=%d result=%s status=%d functionalMs=%.3f marginMs=%.3f cpuMs=%.3f previewCalls=%d verificationCalls=%d totalEntities=%d sql=%d sqlSource=hibernate-statistics%n",
                        round, error == null ? "PASS" : "FAIL",
                        error == null ? 200 : error instanceof ResponseStatusException status ? status.getStatusCode().value() : 0,
                        elapsed, 3500 - elapsed, cpuDelta / 1e6, calls(derivation, "resolverPreview") - previewCalls,
                        calls(verifier, "verificar") - verificationCalls,
                        stats.getEntityLoadCount() - entities, stats.getPrepareStatementCount() - sqlStart);
                System.out.println("COLD_CGROUP_BEFORE " + throttle);
                System.out.println("COLD_CGROUP_AFTER " + cgroup("cpu.stat"));
                if (error != null) error.printStackTrace(System.out);
                // Hibernate prepared statements exclude coordinator SET LOCAL issued through direct JDBC.
                // Query totals below are cumulative for this JVM, not wire-SQL tracing or per-round counts.
                for (String query : stats.getQueries()) {
                    var queryStats = stats.getQueryStatistics(query);
                    if (queryStats.getExecutionCount() == 0) continue;
                    System.out.printf("COLD_QUERY cumulative=true executions=%d rows=%d query=%s%n",
                            queryStats.getExecutionCount(), queryStats.getExecutionRowCount(),
                            query.replaceAll("\\s+", " "));
                }
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(() -> ((AtomicInteger) field(remote, "active")).get() == 0);
                call(fixture, "assertPoolFree");
                System.out.println("COLD_ROUND_CLEANUP round=" + round + " handlers=0 jdbcActive=0 jdbcWaiting=0");
            }
        } finally {
            try { if (prepared) fixture.cleanup(); }
            finally {
                try { if (context != null) context.close(); }
                finally { LocalidadesConsultaCapacidadePostgres17IntegrationTest.stopOwnedResources(); }
            }
        }
        System.out.println("COLD_CLEANUP=PASS failures=" + failures);
        if (failures != 0) System.exit(4);
    }

    private static long calls(Object spy, String methodPrefix) {
        return mockingDetails(spy).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().startsWith(methodPrefix)).count();
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        var method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(item -> item.getName().equals(name) && item.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private static String cgroup(String name) throws Exception {
        return Files.readString(Path.of("/sys/fs/cgroup", name)).trim().replace('\n', ';');
    }

    private static String fingerprint(Object items) throws Exception {
        String text = ((Collection<?>) items).stream().map(Objects::toString).sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
