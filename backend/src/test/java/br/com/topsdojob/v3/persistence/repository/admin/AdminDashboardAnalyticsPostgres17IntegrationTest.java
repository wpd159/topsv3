package br.com.topsdojob.v3.persistence.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
        named = "ADMIN_DASHBOARD_POSTGRES17_ENABLED",
        matches = "true")
class AdminDashboardAnalyticsPostgres17IntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

    @Test
    void executaConsultasAgregadasNaEscalaDaPreProducaoSemNMaisUm() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-dashboard-" + suffix + "-net";
        String container = "topsv3-dashboard-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_dashboard",
                "-e", "POSTGRES_USER=topsv3test",
                "-e", "POSTGRES_PASSWORD",
                "postgres:17-alpine");
        try {
            awaitPostgres(container, credential);
            command(
                    Map.of("FLYWAY_PASSWORD", credential),
                    "docker", "run", "--pull=never", "--rm", "--network", network,
                    "-e", "FLYWAY_PASSWORD",
                    "-v", MIGRATIONS + ":/flyway/sql:ro",
                    "flyway/flyway:12.10.0",
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_dashboard",
                    "-user=topsv3test",
                    "-locations=filesystem:/flyway/sql",
                    "migrate");

            int port = mappedPort(container);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_dashboard",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedAtProductionScale(jdbc);
            AdminDashboardAnalyticsJdbcRepository repository =
                    new AdminDashboardAnalyticsJdbcRepository(
                            new NamedParameterJdbcTemplate(dataSource));

            assertThat(repository.serieDiaria(
                    LocalDate.of(2026, 7, 22),
                    LocalDate.of(2026, 7, 28))).hasSize(1);
            assertThat(repository.topWhatsappHoje(LocalDate.of(2026, 7, 28), 13))
                    .hasSize(13)
                    .allSatisfy(item -> assertThat(item.cliques()).isPositive());

            OffsetDateTime now = OffsetDateTime.parse("2026-07-28T15:00:00Z");
            assertThat(repository.desempenhoPublicados(now)).hasSize(700);
            long startedAt = System.nanoTime();
            var result = repository.desempenhoPublicados(now);
            Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(result).hasSize(700);
            assertThat(duration).isLessThan(Duration.ofSeconds(5));
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seedAtProductionScale(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, versao
                ) VALUES (
                  '10000000-0000-0000-0000-000000000001',
                  'Fixture Dashboard', 'ATIVO', 'ANUNCIANTE',
                  '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z', 0
                )
                """);
        jdbc.update("""
                INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em)
                VALUES (
                  '20000000-0000-0000-0000-000000000001',
                  'GO', 'Goias', 'goias', '2026-07-01T10:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO cidade (
                  id, estado_id, nome, nome_normalizado, slug, criado_em
                ) VALUES (
                  '30000000-0000-0000-0000-000000000001',
                  '20000000-0000-0000-0000-000000000001',
                  'Goiania', 'goiania', 'goiania', '2026-07-01T10:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria,
                  publicado_em, criado_em, atualizado_em, versao
                )
                SELECT
                  md5('dashboard-anuncio-' || g)::uuid,
                  '10000000-0000-0000-0000-000000000001',
                  'dashboard-' || g,
                  'Dashboard ' || g,
                  'PUBLICADO',
                  'APROVADO',
                  'ACOMPANHANTE_FEMININA',
                  '2026-07-01T10:00:00Z',
                  '2026-07-01T10:00:00Z',
                  '2026-07-01T10:00:00Z',
                  0
                FROM generate_series(1, 700) g
                """);
        jdbc.update("""
                INSERT INTO anuncio_localizacao (
                  anuncio_id, estado_id, cidade_id, criado_em, atualizado_em
                )
                SELECT
                  md5('dashboard-anuncio-' || g)::uuid,
                  '20000000-0000-0000-0000-000000000001',
                  '30000000-0000-0000-0000-000000000001',
                  '2026-07-01T10:00:00Z',
                  '2026-07-01T10:00:00Z'
                FROM generate_series(1, 700) g
                """);
        jdbc.update("""
                INSERT INTO evento_visualizacao (
                  id, anuncio_id, request_id, criado_em
                )
                SELECT
                  md5('dashboard-view-' || g)::uuid,
                  md5('dashboard-anuncio-' || ((g - 1) % 700 + 1))::uuid,
                  'native:dashboard:view:' || g,
                  '2026-07-28T12:00:00Z'
                FROM generate_series(1, 30319) g
                """);
        jdbc.update("""
                INSERT INTO clique_whatsapp (
                  id, anuncio_id, permitido, request_id, criado_em
                )
                SELECT
                  md5('dashboard-click-' || g)::uuid,
                  md5('dashboard-anuncio-' || ((g - 1) % 700 + 1))::uuid,
                  true,
                  'native:dashboard:click:' || g,
                  '2026-07-28T12:00:00Z'
                FROM generate_series(1, 2805) g
                """);
        jdbc.update("""
                INSERT INTO agregado_visualizacao_diaria (
                  id, anuncio_id, data_referencia, total_visualizacoes,
                  visitantes_estimados, atualizado_em
                )
                SELECT
                  md5('dashboard-agg-view-' || anuncio_id)::uuid,
                  anuncio_id,
                  DATE '2026-07-28',
                  count(*),
                  count(*),
                  '2026-07-28T15:00:00Z'
                FROM evento_visualizacao
                GROUP BY anuncio_id
                """);
        jdbc.update("""
                INSERT INTO agregado_clique_whatsapp_diario (
                  id, anuncio_id, data_referencia, total_cliques,
                  visitantes_estimados, atualizado_em
                )
                SELECT
                  md5('dashboard-agg-click-' || anuncio_id)::uuid,
                  anuncio_id,
                  DATE '2026-07-28',
                  count(*),
                  count(*),
                  '2026-07-28T15:00:00Z'
                FROM clique_whatsapp
                GROUP BY anuncio_id
                """);
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                    "--dbname", "topsv3_dashboard") == 0) {
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
