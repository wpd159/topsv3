package br.com.topsdojob.v3.persistence.repository.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "ADMIN_USUARIO_EXCLUSAO_POSTGRES17_ENABLED", matches = "true")
class AdminUsuarioExclusaoPostgres17IntegrationTest {

    private static final UUID ADMIN = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ELIGIBLE = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID WITH_HISTORY = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID REUSED = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID AUDIT_ONLY = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final Path MIGRATIONS = Path.of(
            "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

    @Test
    void validaExclusaoFisicaAnonimizacaoReutilizacaoERollback() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String network = "topsv3-user-delete-" + suffix + "-net";
        String container = "topsv3-user-delete-" + suffix + "-pg17";
        String credential = UUID.randomUUID().toString() + UUID.randomUUID();

        command("docker", "network", "create", network);
        command(
                Map.of("POSTGRES_PASSWORD", credential),
                "docker", "run", "--pull=never", "-d", "--name", container,
                "--network", network,
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=topsv3_user_delete",
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
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_user_delete",
                    "-user=topsv3test",
                    "-locations=filesystem:/flyway/sql",
                    "migrate");

            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:" + mappedPort(container)
                            + "/topsv3_user_delete",
                    "topsv3test",
                    credential);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);
            AdminUsuarioExclusaoJdbcRepository repository =
                    new AdminUsuarioExclusaoJdbcRepository(
                            new NamedParameterJdbcTemplate(dataSource));
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));

            assertThat(repository.analisar(ELIGIBLE).exigeAnonimizacao()).isFalse();
            assertThat(repository.analisar(WITH_HISTORY).exigeAnonimizacao()).isTrue();
            assertThat(repository.analisar(WITH_HISTORY).tiposVinculo())
                    .contains("POSSUI_SALDO_OU_LEDGER");
            assertThat(repository.analisar(AUDIT_ONLY).exigeAnonimizacao()).isTrue();
            assertThat(repository.analisar(AUDIT_ONLY).tiposVinculo())
                    .contains("POSSUI_HISTORICO_OPERACIONAL");
            transaction.executeWithoutResult(status ->
                    repository.anonymizeAuxiliaryData(
                            AUDIT_ONLY,
                            List.of(),
                            OffsetDateTime.now()));
            assertThat(jdbc.queryForObject("""
                    SELECT antes_json::text || depois_json::text
                    FROM auditoria_evento
                    WHERE recurso_tipo = 'USUARIO'
                      AND recurso_id = ?
                    """, String.class, AUDIT_ONLY))
                    .doesNotContain("qa.auditoria")
                    .contains("dadosPessoaisOcultos");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM auditoria_evento
                    WHERE recurso_tipo = 'USUARIO'
                      AND recurso_id = ?
                    """, Long.class, AUDIT_ONLY)).isOne();

            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                repository.deleteTechnicalLinks(ELIGIBLE);
                throw new IllegalStateException("falha sintetica");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(count(jdbc, "credencial_usuario", "usuario_id", ELIGIBLE)).isOne();
            assertThat(count(jdbc, "sessao_usuario", "usuario_id", ELIGIBLE)).isOne();
            assertThat(count(jdbc, "token_seguranca", "usuario_id", ELIGIBLE)).isOne();

            transaction.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO auditoria_evento (
                          id, ator_usuario_id, acao, recurso_tipo, recurso_id,
                          antes_json, depois_json, request_id, origem, resultado, criado_em
                        ) VALUES (
                          ?, ?, 'USUARIO_EXCLUIDO_FISICAMENTE', 'USUARIO', ?,
                          '{"status":"PRESENTE"}',
                          '{"status":"EXCLUIDO","estrategia":"EXCLUSAO_FISICA","idempotencyHash":"qa-hash"}',
                          'qa-delete-request', 'ADMIN', 'SUCESSO', now()
                        )
                        """, UUID.randomUUID(), ADMIN, ELIGIBLE);
                repository.deleteTechnicalLinks(ELIGIBLE);
                assertThat(jdbc.update("DELETE FROM usuario WHERE id = ?", ELIGIBLE)).isOne();
            });

            assertThat(count(jdbc, "usuario", "id", ELIGIBLE)).isZero();
            assertThat(count(jdbc, "credencial_usuario", "usuario_id", ELIGIBLE)).isZero();
            assertThat(count(jdbc, "sessao_usuario", "usuario_id", ELIGIBLE)).isZero();
            assertThat(count(jdbc, "token_seguranca", "usuario_id", ELIGIBLE)).isZero();
            assertThat(count(jdbc, "outbox_evento", "aggregate_id", ELIGIBLE)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM auditoria_evento
                    WHERE recurso_id = ?
                      AND acao = 'USUARIO_EXCLUIDO_FISICAMENTE'
                    """, Long.class, ELIGIBLE)).isOne();
            assertThat(repository.exclusaoConcluida(ELIGIBLE, ADMIN, "qa-hash"))
                    .contains("EXCLUSAO_FISICA");

            transaction.executeWithoutResult(status -> {
                repository.deleteTechnicalLinks(WITH_HISTORY);
                jdbc.update("""
                        UPDATE usuario
                        SET nome = 'Conta excluida',
                            nome_civil = NULL,
                            email_normalizado = ?,
                            telefone_normalizado = NULL,
                            cpf_normalizado = NULL,
                            data_nascimento = NULL,
                            email_verificado_em = NULL,
                            telefone_verificado_em = NULL,
                            status = 'EXCLUIDO',
                            desativado_em = now(),
                            exclusao_tipo = 'EXCLUSAO_COM_ANONIMIZACAO',
                            excluido_em = now(),
                            excluido_por = ?,
                            atualizado_em = now()
                        WHERE id = ?
                        """,
                        "conta-excluida+" + WITH_HISTORY + "@topsdojob.invalid",
                        ADMIN,
                        WITH_HISTORY);
            });
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM usuario WHERE id = ?",
                    String.class,
                    WITH_HISTORY)).isEqualTo("EXCLUIDO");
            assertThat(count(jdbc, "saldo_credito_usuario", "usuario_id", WITH_HISTORY)).isOne();

            jdbc.update("""
                    INSERT INTO usuario (
                      id, nome, nome_civil, email_normalizado, telefone_normalizado,
                      cpf_normalizado, status, tipo_conta, criado_em, atualizado_em, versao
                    ) VALUES (
                      ?, 'Nova Conta QA', 'Nova Conta QA', 'qa.historico@example.invalid',
                      '+5562999999901', '12345678909', 'PENDENTE', 'ANUNCIANTE',
                      now(), now(), 0
                    )
                    """, REUSED);
            assertThat(count(jdbc, "usuario", "id", REUSED)).isOne();
            assertThat(count(jdbc, "saldo_credito_usuario", "usuario_id", REUSED)).isZero();
        } finally {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, email_normalizado, telefone_normalizado, cpf_normalizado,
                  status, tipo_conta,
                  criado_em, atualizado_em, versao
                ) VALUES
                  (?, 'QA Admin', 'qa.admin@example.invalid', NULL, NULL,
                      'ATIVO', 'STAFF', now(), now(), 0),
                  (?, 'QA Excluivel', 'qa.excluivel@example.invalid', NULL, NULL,
                      'PENDENTE', 'ANUNCIANTE', now(), now(), 0),
                  (?, 'QA Historico', 'qa.historico@example.invalid', '+5562999999901',
                      '12345678909', 'PENDENTE', 'ANUNCIANTE', now(), now(), 0),
                  (?, 'QA Auditoria', 'qa.auditoria@example.invalid', NULL, NULL,
                      'PENDENTE', 'ANUNCIANTE', now(), now(), 0)
                """, ADMIN, ELIGIBLE, WITH_HISTORY, AUDIT_ONLY);
        jdbc.update("""
                INSERT INTO papel_usuario (usuario_id, papel, criado_por, criado_em)
                VALUES
                  (?, 'ADMIN', ?, now()),
                  (?, 'USUARIO', ?, now()),
                  (?, 'USUARIO', ?, now()),
                  (?, 'USUARIO', ?, now())
                """, ADMIN, ADMIN, ELIGIBLE, ADMIN, WITH_HISTORY, ADMIN, AUDIT_ONLY, ADMIN);
        jdbc.update("""
                INSERT INTO credencial_usuario (
                  id, usuario_id, senha_hash, algoritmo, alterada_em, criado_em
                ) VALUES (?, ?, 'hash-qa', 'ARGON2ID', now(), now())
                """, UUID.randomUUID(), ELIGIBLE);
        jdbc.update("""
                INSERT INTO sessao_usuario (
                  id, usuario_id, token_sessao_hash, criada_em,
                  expira_inatividade_em, expira_absoluta_em, versao
                ) VALUES (?, ?, 'session-hash-qa', now(), now() + interval '1 hour',
                          now() + interval '2 hours', 0)
                """, UUID.randomUUID(), ELIGIBLE);
        jdbc.update("""
                INSERT INTO token_seguranca (
                  id, usuario_id, tipo, token_hash, expira_em, tentativas, criado_em
                ) VALUES (?, ?, 'CONFIRMACAO_EMAIL', 'token-hash-qa',
                          now() + interval '1 hour', 0, now())
                """, UUID.randomUUID(), ELIGIBLE);
        jdbc.update("""
                INSERT INTO saldo_credito_usuario (
                  usuario_id, saldo_atual, atualizado_em, versao
                ) VALUES (?, 0, now(), 0), (?, 1, now(), 0)
                """, ELIGIBLE, WITH_HISTORY);
        jdbc.update("""
                INSERT INTO outbox_evento (
                  id, aggregate_tipo, aggregate_id, tipo_evento, payload_json,
                  status, idempotency_key, tentativas, criado_em, atualizado_em
                ) VALUES (
                  ?, 'USUARIO', ?, 'AUTH_CONFIRMACAO_CONTA_SOLICITADA', '{}',
                  'PENDENTE', 'qa-auth-delete', 0, now(), now()
                )
                """, UUID.randomUUID(), ELIGIBLE);
        jdbc.update("""
                INSERT INTO auditoria_evento (
                  id, ator_usuario_id, acao, recurso_tipo, recurso_id,
                  antes_json, depois_json, request_id, origem, resultado, criado_em
                ) VALUES (
                  ?, ?, 'USUARIO_ATUALIZADO', 'USUARIO', ?,
                  '{"email":"qa.auditoria@example.invalid"}',
                  '{"email":"qa.auditoria.alterado@example.invalid"}',
                  'qa-audit-history', 'ADMIN', 'SUCESSO', now()
                )
                """, UUID.randomUUID(), ADMIN, AUDIT_ONLY);
    }

    private static long count(
            JdbcTemplate jdbc,
            String table,
            String column,
            UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                id);
    }

    private static void awaitPostgres(String container, String credential) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (commandIgnoringFailure(
                    Map.of("PGPASSWORD", credential),
                    "docker", "exec", "-e", "PGPASSWORD", container,
                    "pg_isready", "--host", "127.0.0.1",
                    "--username", "topsv3test", "--dbname", "topsv3_user_delete") == 0) {
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

    private static String command(Map<String, String> environment, String... args)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
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
