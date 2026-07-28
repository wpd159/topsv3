package br.com.topsdojob.v3.persistence.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.admin.pagamentos.RelatorioReceitaFiltro;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "ADMIN_RECEITA_POSTGRES17_ENABLED", matches = "true")
class AdminRelatorioReceitaPostgres17IntegrationTest {

    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

    @Test
    void reconciliaPagamentoConfirmadoSemContarEventosRetriesOuAjustes() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-receita-" + suffix + "-net";
        String container = "topsv3-receita-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_receita",
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
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_receita",
                    "-user=topsv3test",
                    "-locations=filesystem:/flyway/sql",
                    "migrate");

            int port = mappedPort(container);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_receita",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);
            AdminRelatorioReceitaJdbcRepository repository =
                    new AdminRelatorioReceitaJdbcRepository(new NamedParameterJdbcTemplate(dataSource));
            RelatorioReceitaFiltro filtro = filtro(RelatorioReceitaFiltro.Status.TODOS);

            var metricas = repository.metricas(filtro);
            assertThat(metricas.receitaConfirmada()).isEqualByComparingTo("150.00");
            assertThat(metricas.pagamentosConfirmados()).isEqualTo(2);
            assertThat(metricas.creditosVendidos()).isEqualTo(15);
            assertThat(metricas.pagamentosPendentes()).isEqualTo(1);
            assertThat(metricas.pagamentosFalhos()).isEqualTo(1);
            assertThat(metricas.pagamentosCancelados()).isEqualTo(1);
            assertThat(metricas.pagamentosEstornados()).isEqualTo(1);
            assertThat(repository.evolucaoDiaria(filtro)).singleElement()
                    .satisfies(item -> assertThat(item.receitaConfirmada()).isEqualByComparingTo("150.00"));
            assertThat(repository.distribuicaoPorProduto(filtro)).singleElement()
                    .satisfies(item -> assertThat(item.creditosVendidos()).isEqualTo(15));
            assertThat(repository.transacoes(filtro, 0, 20, "MAIS_RECENTES").total()).isEqualTo(7);

            var somenteConfirmados = repository.metricas(
                    filtro(RelatorioReceitaFiltro.Status.CONFIRMADO));
            assertThat(somenteConfirmados.receitaConfirmada()).isEqualByComparingTo("150.00");
            assertThat(somenteConfirmados.pagamentosConfirmados()).isEqualTo(2);

            var somenteCancelados = repository.metricas(
                    filtro(RelatorioReceitaFiltro.Status.CANCELADO));
            assertThat(somenteCancelados.pagamentosCancelados()).isEqualTo(1);
            assertThat(somenteCancelados.pagamentosEstornados()).isZero();

            var somenteEstornados = repository.metricas(
                    filtro(RelatorioReceitaFiltro.Status.ESTORNADO));
            assertThat(somenteEstornados.pagamentosCancelados()).isZero();
            assertThat(somenteEstornados.pagamentosEstornados()).isEqualTo(1);
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static RelatorioReceitaFiltro filtro(RelatorioReceitaFiltro.Status status) {
        return new RelatorioReceitaFiltro(
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 28),
                OffsetDateTime.parse("2026-07-22T03:00:00Z"),
                OffsetDateTime.parse("2026-07-29T03:00:00Z"),
                status,
                RelatorioReceitaFiltro.Metodo.TODOS,
                null,
                null);
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, email_normalizado, status, tipo_conta, criado_em, atualizado_em, versao
                ) VALUES (
                  '10000000-0000-0000-0000-000000000001',
                  'QA Financeiro', 'qa.financeiro@example.invalid', 'ATIVO', 'ANUNCIANTE',
                  '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z', 0
                )
                """);
        jdbc.update("""
                INSERT INTO plano_credito (
                  id, codigo, nome, quantidade_creditos, valor, moeda, ativo, criado_em, atualizado_em
                ) VALUES (
                  '20000000-0000-0000-0000-000000000001',
                  'PACOTE_QA', 'Pacote QA', 10, 100.00, 'BRL', true,
                  '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO pagamento (
                  id, usuario_id, plano_credito_id, provedor, metodo, txid, valor, moeda,
                  quantidade_creditos, status_interno, aprovado_em, cancelado_em,
                  idempotency_key, criado_em, atualizado_em
                ) VALUES
                  ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-1', 100.00, 'BRL', 10, 'APROVADO', '2026-07-27T12:00:00Z', null, 'qa-idem-1', '2026-07-27T11:00:00Z', '2026-07-27T12:00:00Z'),
                  ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-2', 50.00, 'BRL', 5, 'APROVADO', '2026-07-27T13:00:00Z', null, 'qa-idem-2', '2026-07-27T11:00:00Z', '2026-07-27T13:00:00Z'),
                  ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-3', 20.00, 'BRL', 2, 'AGUARDANDO_PAGAMENTO', null, null, 'qa-idem-3', '2026-07-27T14:00:00Z', '2026-07-27T14:00:00Z'),
                  ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-4', 30.00, 'BRL', 3, 'ERRO', null, null, 'qa-idem-4', '2026-07-27T15:00:00Z', '2026-07-27T15:00:00Z'),
                  ('30000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-5', 40.00, 'BRL', 4, 'CANCELADO', null, '2026-07-27T16:00:00Z', 'qa-idem-5', '2026-07-27T15:00:00Z', '2026-07-27T16:00:00Z'),
                  ('30000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'EFI', 'PIX', 'qa-tx-6', 10.00, 'BRL', 1, 'ESTORNADO', null, null, 'qa-idem-6', '2026-07-27T17:00:00Z', '2026-07-27T18:00:00Z'),
                  ('30000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000001', null, 'OUTRO_LEGADO', 'LEGADO', 'qa-tx-7', 90.00, 'BRL', 9, 'LEGADO', null, null, 'qa-idem-7', '2026-07-27T19:00:00Z', '2026-07-27T19:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO movimento_credito (
                  id, usuario_id, tipo, direcao, quantidade, saldo_antes, saldo_depois,
                  origem, referencia_tipo, referencia_id, idempotency_key, ator_usuario_id,
                  observacao, criado_em
                ) VALUES
                  ('40000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'ENTRADA', 'CREDITO', 10, 0, 10, 'PAGAMENTO', 'PAGAMENTO', '30000000-0000-0000-0000-000000000001', 'qa-mov-1', null, null, '2026-07-27T12:00:00Z'),
                  ('40000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'AJUSTE', 'CREDITO', 500, 10, 510, 'AJUSTE_ADMIN', null, null, 'qa-ajuste', '10000000-0000-0000-0000-000000000001', 'Fixture de ajuste excluida da receita', '2026-07-27T12:30:00Z')
                """);
        jdbc.update("""
                INSERT INTO pagamento_conciliacao (
                  id, pagamento_id, movimento_credito_id, origem, status,
                  valor_confirmado, creditos_confirmados, aprovado_em, creditado_em, criado_em
                ) VALUES (
                  '50000000-0000-0000-0000-000000000001',
                  '30000000-0000-0000-0000-000000000001',
                  '40000000-0000-0000-0000-000000000001',
                  'WEBHOOK', 'CONCILIADO', 100.00, 10,
                  '2026-07-27T12:00:00Z', '2026-07-27T12:00:00Z', '2026-07-27T12:00:00Z'
                )
                """);
        jdbc.update("""
                INSERT INTO pagamento_evento (
                  id, pagamento_id, provedor, provedor_evento_id, tipo_evento, recebido_em
                ) VALUES
                  ('60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'EFI', 'qa-event-1', 'CONFIRMADO', '2026-07-27T12:00:00Z'),
                  ('60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'EFI', 'qa-event-2', 'RETRY', '2026-07-27T12:01:00Z')
                """);
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                    "--dbname", "topsv3_receita") == 0) {
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
