package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "V046_POSTGRES17_ENABLED", matches = "true")
class V046Postgres17ConcurrencyIntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
    private static final UUID ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID OWNER = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ANUNCIO_A = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID ANUNCIO_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID ANUNCIO_C = UUID.fromString("20000000-0000-4000-8000-000000000003");
    private static final UUID ANUNCIO_D = UUID.fromString("20000000-0000-4000-8000-000000000004");
    private static final UUID BENEFICIO = UUID.fromString("f3000000-0000-4000-8000-000000000002");
    private static final UUID ATIVACAO_ATIVA = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID ATIVACAO_EXPIRADA = UUID.fromString("40000000-0000-4000-8000-000000000002");

    @Test
    void preservaLegadoEImpedeDuplicidadesConcorrentes() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-v046-" + suffix + "-net";
        String container = "topsv3-v046-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_v046",
                "-e", "POSTGRES_USER=topsv3test",
                "-e", "POSTGRES_PASSWORD",
                "postgres:17-alpine");
        try {
            awaitPostgres(container, credential);
            flyway(container, network, credential, "-target=45", "migrate");
            int port = mappedPort(container);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_v046",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedV045(jdbc);

            flyway(container, network, credential, "migrate");
            flyway(container, network, credential, "validate");

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_selecao_administrativa WHERE id = 1 AND anuncio_id = ?",
                    Long.class,
                    ANUNCIO_A)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM ativacao_beneficio WHERE id IN (?, ?) AND inicio_em IS NOT NULL AND fim_em IS NOT NULL",
                    Long.class,
                    ATIVACAO_ATIVA,
                    ATIVACAO_EXPIRADA)).isEqualTo(2L);
            assertThat(jdbc.queryForObject(
                    "SELECT extract(epoch FROM inicio_em)::bigint FROM ativacao_beneficio WHERE id = ?",
                    Long.class,
                    ATIVACAO_ATIVA)).isEqualTo(OffsetDateTime.parse("2026-07-01T00:00:00Z").toEpochSecond());
            assertThat(jdbc.queryForObject(
                    "SELECT extract(epoch FROM fim_em)::bigint FROM ativacao_beneficio WHERE id = ?",
                    Long.class,
                    ATIVACAO_EXPIRADA)).isEqualTo(OffsetDateTime.parse("2026-06-01T00:00:00Z").toEpochSecond());

            assertThat(concurrently(
                    () -> insertStory(dataSource, ANUNCIO_B, "story-b"),
                    () -> insertStory(dataSource, ANUNCIO_C, "story-c")))
                    .containsExactly(true, true);

            assertThat(concurrently(
                    () -> insertStory(dataSource, ANUNCIO_D, "story-d-1"),
                    () -> insertStory(dataSource, ANUNCIO_D, "story-d-2")))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM story_selecao_administrativa WHERE anuncio_id = ? AND ativa",
                    Long.class,
                    ANUNCIO_D)).isEqualTo(1L);

            assertThat(concurrently(
                    () -> insertPendingActivation(dataSource, UUID.randomUUID(), "foto-pendente-1"),
                    () -> insertPendingActivation(dataSource, UUID.randomUUID(), "foto-pendente-2")))
                    .containsExactlyInAnyOrder(true, false);
            UUID pendingId = jdbc.queryForObject(
                    "SELECT id FROM ativacao_beneficio WHERE anuncio_id = ? AND beneficio_id = ? AND status = 'AGUARDANDO_MODERACAO'",
                    UUID.class,
                    ANUNCIO_D,
                    BENEFICIO);

            OffsetDateTime primeiraData = OffsetDateTime.parse("2026-07-31T15:00:00Z");
            OffsetDateTime segundaData = OffsetDateTime.parse("2026-07-31T15:00:01Z");
            assertThat(concurrently(
                    () -> startPendingActivation(dataSource, pendingId, primeiraData),
                    () -> startPendingActivation(dataSource, pendingId, segundaData)))
                    .containsExactlyInAnyOrder(true, false);
            Long inicioEpoch = jdbc.queryForObject(
                    "SELECT extract(epoch FROM inicio_em)::bigint FROM ativacao_beneficio WHERE id = ?",
                    Long.class,
                    pendingId);
            Long duracaoSegundos = jdbc.queryForObject(
                    "SELECT extract(epoch FROM (fim_em - inicio_em))::bigint FROM ativacao_beneficio WHERE id = ?",
                    Long.class,
                    pendingId);
            assertThat(inicioEpoch).isIn(primeiraData.toEpochSecond(), segundaData.toEpochSecond());
            assertThat(duracaoSegundos).isEqualTo(30L * 24L * 60L * 60L);
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seedV045(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
                VALUES
                  (?, 'Admin QA', 'ATIVO', 'STAFF', now(), now(), 0),
                  (?, 'Owner QA', 'ATIVO', 'ANUNCIANTE', now(), now(), 0)
                """, ADMIN, OWNER);
        for (UUID anuncioId : List.of(ANUNCIO_A, ANUNCIO_B, ANUNCIO_C, ANUNCIO_D)) {
            jdbc.update("""
                    INSERT INTO anuncio (
                      id, usuario_id, slug, titulo, status, status_moderacao,
                      categoria, criado_em, atualizado_em, versao
                    ) VALUES (?, ?, ?, 'Anuncio QA', 'PUBLICADO', 'APROVADO',
                              'ACOMPANHANTE_FEMININA', now(), now(), 0)
                    """, anuncioId, OWNER, "qa-" + anuncioId);
        }
        jdbc.update("""
                INSERT INTO story_selecao_administrativa (
                  singleton_id, anuncio_id, ativa, ativado_por, ativado_em,
                  criado_em, atualizado_em, versao
                ) VALUES (1, ?, true, ?, '2026-07-30T15:00:00Z', now(), now(), 0)
                """, ANUNCIO_A, ADMIN);
        jdbc.update("""
                INSERT INTO ativacao_beneficio (
                  id, beneficio_id, usuario_id, anuncio_id, origem,
                  inicio_em, fim_em, status, custo_creditos_snapshot, criado_em
                ) VALUES
                  (?, ?, ?, ?, 'ADMIN', '2026-07-01T00:00:00Z', '2026-08-01T00:00:00Z', 'ATIVA', 0, now()),
                  (?, ?, ?, ?, 'ADMIN', '2026-05-01T00:00:00Z', '2026-06-01T00:00:00Z', 'EXPIRADA', 0, now())
                """,
                ATIVACAO_ATIVA, BENEFICIO, OWNER, ANUNCIO_A,
                ATIVACAO_EXPIRADA, BENEFICIO, OWNER, ANUNCIO_A);
    }

    private static boolean insertStory(
            DriverManagerDataSource dataSource,
            UUID anuncioId,
            String key) throws Exception {
        return insertIgnoringUniqueViolation(dataSource, """
                INSERT INTO story_selecao_administrativa (
                  anuncio_id, ativa, ativado_por, ativado_em, expira_em,
                  idempotency_key, criado_em, atualizado_em, versao
                ) VALUES (?, true, ?, now(), now() + interval '24 hours', ?, now(), now(), 0)
                """, anuncioId, ADMIN, key);
    }

    private static boolean insertPendingActivation(
            DriverManagerDataSource dataSource,
            UUID id,
            String key) throws Exception {
        return insertIgnoringUniqueViolation(dataSource, """
                INSERT INTO ativacao_beneficio (
                  id, beneficio_id, usuario_id, anuncio_id, origem,
                  inicio_em, fim_em, status, custo_creditos_snapshot,
                  idempotency_key, criado_em
                ) VALUES (?, ?, ?, ?, 'CREDITO', null, null,
                          'AGUARDANDO_MODERACAO', 5, ?, now())
                """, id, BENEFICIO, OWNER, ANUNCIO_D, key);
    }

    private static boolean insertIgnoringUniqueViolation(
            DriverManagerDataSource dataSource,
            String sql,
            Object... values) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) {
                    statement.setObject(index + 1, values[index]);
                }
                statement.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    return false;
                }
                throw exception;
            }
        }
    }

    private static boolean startPendingActivation(
            DriverManagerDataSource dataSource,
            UUID id,
            OffsetDateTime inicio) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT status FROM ativacao_beneficio WHERE id = ? FOR UPDATE")) {
                select.setObject(1, id);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next() || !"AGUARDANDO_MODERACAO".equals(result.getString(1))) {
                        connection.rollback();
                        return false;
                    }
                }
            }
            Thread.sleep(100L);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE ativacao_beneficio
                    SET status = 'ATIVA', inicio_em = ?, fim_em = ?
                    WHERE id = ? AND status = 'AGUARDANDO_MODERACAO'
                    """)) {
                update.setObject(1, inicio);
                update.setObject(2, inicio.plusDays(30));
                update.setObject(3, id);
                boolean changed = update.executeUpdate() == 1;
                connection.commit();
                return changed;
            }
        }
    }

    private static List<Boolean> concurrently(
            Callable<Boolean> first,
            Callable<Boolean> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> wrapFirst = () -> {
                ready.countDown();
                start.await();
                return first.call();
            };
            Callable<Boolean> wrapSecond = () -> {
                ready.countDown();
                start.await();
                return second.call();
            };
            var firstResult = executor.submit(wrapFirst);
            var secondResult = executor.submit(wrapSecond);
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void flyway(
            String container,
            String network,
            String credential,
            String... operation) throws Exception {
        String[] base = {
                "docker", "run", "--pull=never", "--rm", "--network", network,
                "-e", "FLYWAY_PASSWORD",
                "-v", MIGRATIONS + ":/flyway/sql:ro",
                "flyway/flyway:12.10.0",
                "-url=jdbc:postgresql://" + container + ":5432/topsv3_v046",
                "-user=topsv3test",
                "-locations=filesystem:/flyway/sql"
        };
        String[] args = new String[base.length + operation.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(operation, 0, args, base.length, operation.length);
        command(Map.of("FLYWAY_PASSWORD", credential), args);
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1",
                    "--username", "topsv3test", "--dbname", "topsv3_v046") == 0) {
                return;
            }
            Thread.sleep(500L);
        }
        throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
    }

    private static int mappedPort(String container) throws Exception {
        String output = command("docker", "port", container, "5432/tcp").trim();
        return Integer.parseInt(output.substring(output.lastIndexOf(':') + 1));
    }

    private static String command(String... args) throws Exception {
        return command(Map.of(), args);
    }

    private static String command(Map<String, String> environment, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
        }
        return output;
    }

    private static int commandIgnoringFailure(String... args) throws Exception {
        return commandIgnoringFailure(Map.of(), args);
    }

    private static int commandIgnoringFailure(
            Map<String, String> environment,
            String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
