package br.com.topsdojob.v3.persistence.faq;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.persistence.repository.faq.FaqRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    initializers = FaqRepositoryPostgres17IntegrationTest.PostgresInitializer.class)
@EnabledIfEnvironmentVariable(
    named = "FAQ_POSTGRES17_ENABLED",
    matches = "true")
class FaqRepositoryPostgres17IntegrationTest {

  private static final UUID ATOR =
      UUID.fromString("10000000-0000-4000-8000-000000000040");
  private static final UUID FAQ =
      UUID.fromString("20000000-0000-4000-8000-000000000040");

  @Autowired
  private FaqRepository repository;

  @Autowired
  private JdbcTemplate jdbc;

  @AfterAll
  static void cleanup() throws Exception {
    PostgresSupport.stop();
  }

  @Test
  void listaSemFiltrosNulosNoPostgresql17SemConverterTermoEmBytea() {
    jdbc.update("""
        INSERT INTO usuario (
          id, nome, status, tipo_conta, criado_em, atualizado_em, versao
        ) VALUES (?, 'faq-qa', 'ATIVO', 'STAFF', now(), now(), 0)
        """, ATOR);
    jdbc.update("""
        INSERT INTO faq_item (
          id, pergunta, resposta, categoria, status, ordem,
          criado_por_usuario_id, atualizado_por_usuario_id, criado_request_id,
          criado_em, atualizado_em, versao
        ) VALUES (
          ?, 'Como funciona a FAQ de QA?',
          'Resposta segura para o teste PostgreSQL 17.',
          'GERAL', 'RASCUNHO', 1, ?, ?, 'faq-postgres-17',
          now(), now(), 0
        )
        """, FAQ, ATOR, ATOR);

    var resultado = repository.buscarAdmin(null, null, null);

    assertThat(resultado)
        .singleElement()
        .satisfies(item -> assertThat(item.getId()).isEqualTo(FAQ));
  }

  static final class PostgresInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
      try {
        PostgresSupport.start();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
            "faq-postgres17",
            Map.of(
                "spring.datasource.url", PostgresSupport.jdbcUrl(),
                "spring.datasource.username", "topsv3test",
                "spring.datasource.password", PostgresSupport.credential(),
                "spring.flyway.enabled", "false",
                "spring.jpa.hibernate.ddl-auto", "validate")));
      } catch (Exception exception) {
        throw new IllegalStateException(
            "falha ao preparar PostgreSQL 17 para FAQs", exception);
      }
    }
  }

  static final class PostgresSupport {

    private static final Path MIGRATIONS = Path.of(
        "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
    private static final String SUFFIX =
        UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String NETWORK = "topsv3-faq-" + SUFFIX + "-net";
    private static final String CONTAINER = "topsv3-faq-" + SUFFIX + "-pg17";
    private static final String CREDENTIAL =
        UUID.randomUUID().toString() + UUID.randomUUID();
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
            "-e", "POSTGRES_DB=topsv3_faq",
            "-e", "POSTGRES_USER=topsv3test",
            "-e", "POSTGRES_PASSWORD",
            "postgres:17-alpine");
        awaitPostgres();
        command(
            Map.of("FLYWAY_PASSWORD", CREDENTIAL),
            "docker", "run", "--pull=never", "--rm", "--network", NETWORK,
            "-e", "FLYWAY_PASSWORD",
            "-v", MIGRATIONS + ":/flyway/sql:ro",
            "flyway/flyway:12.10.0-alpine",
            "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_faq",
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
      return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_faq";
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
            "--dbname", "topsv3_faq") == 0) {
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

    private static String command(
        Map<String, String> environment,
        String... args) throws Exception {
      ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
      builder.environment().putAll(environment);
      Process process = builder.start();
      String output = new String(
          process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exit = process.waitFor();
      if (exit != 0) {
        throw new IllegalStateException(
            String.join(" ", args) + " falhou: " + output);
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
