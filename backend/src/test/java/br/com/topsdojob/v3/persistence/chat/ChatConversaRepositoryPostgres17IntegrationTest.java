package br.com.topsdojob.v3.persistence.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.persistence.repository.chat.ChatConversaRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(
        classes = TopsDoJobBackendApplication.class,
        initializers = ChatConversaRepositoryPostgres17IntegrationTest.PostgresInitializer.class)
@EnabledIfEnvironmentVariable(
        named = "CHAT_INTERNO_POSTGRES17_ENABLED",
        matches = "true")
class ChatConversaRepositoryPostgres17IntegrationTest {

    private static final UUID USUARIO_A = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID USUARIO_B = UUID.fromString("20000000-0000-4000-8000-000000000002");
    private static final UUID CONVERSA = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID MENSAGEM = UUID.fromString("40000000-0000-4000-8000-000000000004");

    @Autowired
    private ChatConversaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void cleanup() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    void listaResumoNativoComUltimaMensagemENaoLidas() {
        seed();

        var resumos = repository.listarResumos(USUARIO_B);

        assertThat(resumos).hasSize(1);
        Object[] resumo = resumos.get(0);
        assertThat(resumo[0]).isEqualTo(CONVERSA);
        assertThat(resumo[1]).isEqualTo("qa-a");
        assertThat(resumo[2]).isEqualTo("Mensagem QA");
        assertThat(resumo[3]).isInstanceOf(Instant.class);
        assertThat(((Number) resumo[4]).longValue()).isEqualTo(1L);
    }

    private void seed() {
        jdbc.update("""
                INSERT INTO usuario (
                  id, nome, status, tipo_conta, criado_em, atualizado_em, versao
                ) VALUES
                  (?, 'qa-a', 'ATIVO', 'ANUNCIANTE', now(), now(), 0),
                  (?, 'qa-b', 'ATIVO', 'ANUNCIANTE', now(), now(), 0)
                """, USUARIO_A, USUARIO_B);
        jdbc.update("""
                INSERT INTO chat_conversa (
                  id, participante_a_id, participante_b_id, criado_request_id,
                  criado_em, atualizado_em, versao
                ) VALUES (?, ?, ?, 'request-chat-repository', now(), now(), 0)
                """, CONVERSA, USUARIO_A, USUARIO_B);
        jdbc.update("""
                INSERT INTO chat_mensagem (
                  id, conversa_id, remetente_usuario_id, destinatario_usuario_id,
                  corpo, idempotency_key, request_id, criado_em
                ) VALUES (
                  ?, ?, ?, ?, 'Mensagem QA', 'chat-repository-0001',
                  'request-chat-repository', now()
                )
                """, MENSAGEM, CONVERSA, USUARIO_A, USUARIO_B);
    }

    static final class PostgresInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                        "chat-postgres17",
                        Map.of(
                                "spring.datasource.url", PostgresSupport.jdbcUrl(),
                                "spring.datasource.username", "topsv3test",
                                "spring.datasource.password", PostgresSupport.credential(),
                                "spring.flyway.enabled", "false",
                                "spring.jpa.hibernate.ddl-auto", "validate")));
            } catch (Exception exception) {
                throw new IllegalStateException("falha ao preparar PostgreSQL 17 para o chat", exception);
            }
        }
    }

    static final class PostgresSupport {

        private static final Path MIGRATIONS = Path.of(
                "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
        private static final String SUFFIX =
                UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        private static final String NETWORK = "topsv3-chat-" + SUFFIX + "-net";
        private static final String CONTAINER = "topsv3-chat-" + SUFFIX + "-pg17";
        private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
        private static int port;
        private static boolean started;

        private PostgresSupport() {
        }

        static synchronized void start() throws Exception {
            if (started) {
                return;
            }
            command("docker", "network", "create", NETWORK);
            try {
                command(
                        Map.of("POSTGRES_PASSWORD", CREDENTIAL),
                        "docker", "run", "--pull=never", "-d", "--name", CONTAINER,
                        "--network", NETWORK,
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_chat",
                        "-e", "POSTGRES_USER=topsv3test",
                        "-e", "POSTGRES_PASSWORD",
                        "postgres:17-alpine");
                awaitPostgres();
                command(
                        Map.of("FLYWAY_PASSWORD", CREDENTIAL),
                        "docker", "run", "--pull=never", "--rm", "--network", NETWORK,
                        "-e", "FLYWAY_PASSWORD",
                        "-v", MIGRATIONS + ":/flyway/sql:ro",
                        "flyway/flyway:12.10.0",
                        "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_chat",
                        "-user=topsv3test",
                        "-locations=filesystem:/flyway/sql",
                        "migrate");
                port = mappedPort();
                started = true;
            } catch (Exception exception) {
                stop();
                throw exception;
            }
        }

        static synchronized void stop() throws Exception {
            commandIgnoringFailure("docker", "rm", "-f", CONTAINER);
            commandIgnoringFailure("docker", "network", "rm", NETWORK);
            started = false;
        }

        static String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_chat";
        }

        static String credential() {
            return CREDENTIAL;
        }

        private static void awaitPostgres() throws Exception {
            for (int attempt = 0; attempt < 60; attempt++) {
                if (commandIgnoringFailure(
                        Map.of("PGPASSWORD", CREDENTIAL),
                        "docker", "exec", "-e", "PGPASSWORD", CONTAINER,
                        "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
                        "--dbname", "topsv3_chat") == 0) {
                    return;
                }
                Thread.sleep(500L);
            }
            throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
        }

        private static int mappedPort() throws Exception {
            String output = command("docker", "port", CONTAINER, "5432/tcp").trim();
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
}
