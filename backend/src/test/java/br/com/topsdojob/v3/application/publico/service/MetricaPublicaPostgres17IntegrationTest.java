package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.entity.metrica.CliqueWhatsappEntity;
import br.com.topsdojob.v3.persistence.entity.metrica.EventoVisualizacaoEntity;
import br.com.topsdojob.v3.persistence.repository.MetricaPublicaWriteRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DispositivoMetrica;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
        named = "METRICA_PUBLICA_POSTGRES17_ENABLED",
        matches = "true")
class MetricaPublicaPostgres17IntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

    @Test
    void persisteEventoEAgregadoUmaUnicaVezSemAlterarSaldoHistorico() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-metrica-publica-" + suffix + "-net";
        String container = "topsv3-metrica-publica-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_metricas",
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
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_metricas",
                    "-user=topsv3test",
                    "-locations=filesystem:/flyway/sql",
                    "migrate");

            int port = mappedPort(container);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_metricas",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);

            MetricaPublicaPersistenceService service = new MetricaPublicaPersistenceService(
                    new MetricaPublicaWriteRepository(jdbc));
            UUID anuncioId = UUID.fromString("20000000-0000-0000-0000-000000000001");
            OffsetDateTime instante = OffsetDateTime.parse("2026-07-26T02:30:00Z");
            EventoVisualizacaoEntity visualizacao = EventoVisualizacaoEntity.registrar(
                    UUID.fromString("30000000-0000-0000-0000-000000000001"),
                    anuncioId,
                    "visitante",
                    "ip",
                    "ua",
                    "referer",
                    "BR",
                    "SP",
                    "Sao Paulo",
                    DispositivoMetrica.MOBILE,
                    "native:view:test",
                    instante);
            CliqueWhatsappEntity clique = CliqueWhatsappEntity.registrar(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    anuncioId,
                    "visitante",
                    "ip",
                    "ua",
                    "BR",
                    "SP",
                    "Sao Paulo",
                    DispositivoMetrica.MOBILE,
                    true,
                    null,
                    "native:click:test",
                    instante);

            assertThat(service.registrarVisualizacao(visualizacao)).isTrue();
            assertThat(service.registrarVisualizacao(visualizacao)).isFalse();
            assertThat(service.registrarClique(clique)).isTrue();
            assertThat(service.registrarClique(clique)).isFalse();

            assertThat(number(jdbc, "SELECT count(*) FROM evento_visualizacao")).isEqualTo(1);
            assertThat(number(jdbc, "SELECT sum(total_visualizacoes) FROM agregado_visualizacao_diaria"))
                    .isEqualTo(1);
            assertThat(number(jdbc, "SELECT count(*) FROM clique_whatsapp")).isEqualTo(1);
            assertThat(number(jdbc, "SELECT sum(total_cliques) FROM agregado_clique_whatsapp_diario"))
                    .isEqualTo(1);
            assertThat(number(jdbc, """
                    SELECT i.total_visualizacoes + count(e.id)
                    FROM agregado_visualizacao_inicial i
                    LEFT JOIN evento_visualizacao e
                      ON e.anuncio_id = i.anuncio_id
                     AND e.criado_em > i.snapshot_corte_em
                    WHERE i.anuncio_id = '20000000-0000-0000-0000-000000000001'
                    GROUP BY i.total_visualizacoes
                    """)).isEqualTo(8);
            assertThat(jdbc.queryForObject(
                    "SELECT data_referencia::text FROM agregado_visualizacao_diaria",
                    String.class)).isEqualTo("2026-07-25");
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, versao
                ) VALUES (
                  '10000000-0000-0000-0000-000000000001', 'Fixture', 'ATIVO', 'ANUNCIANTE',
                  '2026-07-25T10:00:00Z', '2026-07-25T10:00:00Z', 0
                )
                """);
        jdbc.update("""
                INSERT INTO anuncio (
                  id, usuario_id, slug, titulo, status, status_moderacao, categoria,
                  criado_em, atualizado_em, versao
                ) VALUES (
                  '20000000-0000-0000-0000-000000000001',
                  '10000000-0000-0000-0000-000000000001',
                  'fixture-metrica', 'Fixture metrica', 'PUBLICADO', 'APROVADO',
                  'MASSAGENS', '2026-07-25T10:00:00Z', '2026-07-25T10:00:00Z', 0
                )
                """);
        jdbc.update("""
                INSERT INTO importacao_execucao (
                  id, sistema_origem, status, iniciado_em, resumo_json, criado_em
                ) VALUES (
                  '50000000-0000-0000-0000-000000000001',
                  'FIXTURE', 'CONCLUIDA', '2026-07-25T10:00:00Z',
                  '{}'::jsonb, '2026-07-25T10:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO agregado_visualizacao_inicial (
                  id, anuncio_id, execucao_id, total_visualizacoes,
                  snapshot_fingerprint, origem_hash, snapshot_corte_em,
                  criado_em, atualizado_em
                ) VALUES (
                  '60000000-0000-0000-0000-000000000001',
                  '20000000-0000-0000-0000-000000000001',
                  '50000000-0000-0000-0000-000000000001',
                  7, 'fixture', md5('fixture'), '2026-07-25T10:00:00Z',
                  '2026-07-25T10:00:00Z', '2026-07-25T10:00:00Z'
                )
                """);
    }

    private static long number(JdbcTemplate jdbc, String sql) {
        Number value = jdbc.queryForObject(sql, Number.class);
        return value == null ? 0 : value.longValue();
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                    "--dbname", "topsv3_metricas") == 0) {
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
