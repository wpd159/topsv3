package br.com.topsdojob.v3.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.persistence.repository.admin.AdminStaffJdbcRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "ADMIN_STAFF_POSTGRES17_ENABLED", matches = "true")
class AdminStaffPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

  @Test
  void listaFiltraEPreservaPermissoesEfetivasSemExporCredencial() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-admin-staff-" + suffix + "-net";
    String container = "topsv3-admin-staff-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    command("docker", "network", "create", network);
    command(
        Map.of("POSTGRES_PASSWORD", credential),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network,
        "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=topsv3_admin_staff",
        "-e", "POSTGRES_USER=topsv3test",
        "-e", "POSTGRES_PASSWORD",
        "postgres:17-alpine");
    try {
      awaitPostgres(container, credential);
      migrate(container, network, credential);
      int port = mappedPort(container);
      var dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_admin_staff",
          "topsv3test",
          credential);
      var jdbc = new JdbcTemplate(dataSource);
      seed(jdbc);
      var repository = new AdminStaffJdbcRepository(new NamedParameterJdbcTemplate(dataSource));

      assertThat(repository.listar("moderador", null, true, "RECENTES", 0, 20))
          .singleElement()
          .satisfies(staff -> {
            assertThat(staff.papel()).isEqualTo("MODERADOR");
            assertThat(staff.acessoPendente()).isTrue();
          });
      assertThat(repository.contar(null, "ADMIN", true)).isEqualTo(1);
      assertThat(repository.listar(null, null, null, "ANTIGOS", 0, 1))
          .singleElement()
          .satisfies(staff -> assertThat(staff.papel()).isEqualTo("ADMIN"));

      var indicators = repository.indicadores();
      assertThat(indicators.total()).isEqualTo(2);
      assertThat(indicators.ativos()).isEqualTo(2);
      assertThat(indicators.administradores()).isEqualTo(1);
      assertThat(indicators.moderadores()).isEqualTo(1);

      UUID moderatorId = UUID.fromString("10000000-0000-4000-8000-000000000002");
      assertThat(repository.permissoes(moderatorId))
          .extracting(permissao -> permissao.codigo())
          .contains("ANUNCIO_LER", "ANUNCIO_MODERAR")
          .doesNotContain("ADMIN_CONFIGURAR");
      assertThat(repository.historico(moderatorId))
          .singleElement()
          .satisfies(event -> assertThat(event.requestId()).isEqualTo("req-staff-seed"));
      assertThat(repository.bloquearAdministradoresAtivos()).containsExactly(
          UUID.fromString("10000000-0000-4000-8000-000000000001"));
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static void seed(JdbcTemplate jdbc) {
    jdbc.update("""
        INSERT INTO usuario (
          id, nome, email_normalizado, status, tipo_conta,
          email_verificado_em, criado_em, atualizado_em, versao
        ) VALUES
          (
            '10000000-0000-4000-8000-000000000001', 'Admin QA',
            'admin.staff@example.invalid', 'ATIVO', 'STAFF',
            '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z', '2026-07-01T10:00:00Z', 0
          ),
          (
            '10000000-0000-4000-8000-000000000002', 'Moderador QA',
            'moderador.staff@example.invalid', 'ATIVO', 'STAFF',
            '2026-07-02T10:00:00Z', '2026-07-02T10:00:00Z', '2026-07-02T10:00:00Z', 0
          ),
          (
            '10000000-0000-4000-8000-000000000003', 'Usuario fora do staff',
            'usuario@example.invalid', 'ATIVO', 'ANUNCIANTE',
            '2026-07-03T10:00:00Z', '2026-07-03T10:00:00Z', '2026-07-03T10:00:00Z', 0
          )
        """);
    jdbc.update("""
        INSERT INTO credencial_usuario (
          id, usuario_id, senha_hash, algoritmo, alterada_em, precisa_redefinir, criado_em
        ) VALUES
          (
            '20000000-0000-4000-8000-000000000001',
            '10000000-0000-4000-8000-000000000001',
            '$2a$10$hash-admin', 'BCRYPT', '2026-07-01T10:00:00Z', false, '2026-07-01T10:00:00Z'
          ),
          (
            '20000000-0000-4000-8000-000000000002',
            '10000000-0000-4000-8000-000000000002',
            '$2a$10$hash-moderador', 'BCRYPT', '2026-07-02T10:00:00Z', true, '2026-07-02T10:00:00Z'
          )
        """);
    jdbc.update("""
        INSERT INTO papel_usuario (usuario_id, papel, criado_em) VALUES
          ('10000000-0000-4000-8000-000000000001', 'ADMIN', '2026-07-01T10:00:00Z'),
          ('10000000-0000-4000-8000-000000000002', 'MODERADOR', '2026-07-02T10:00:00Z'),
          ('10000000-0000-4000-8000-000000000003', 'USUARIO', '2026-07-03T10:00:00Z')
        """);
    jdbc.update("""
        INSERT INTO auditoria_evento (
          id, ator_usuario_id, acao, recurso_tipo, recurso_id, request_id,
          origem, resultado, criado_em
        ) VALUES (
          '30000000-0000-4000-8000-000000000001',
          '10000000-0000-4000-8000-000000000001',
          'STAFF_CRIAR', 'STAFF',
          '10000000-0000-4000-8000-000000000002',
          'req-staff-seed', 'ADMIN', 'SUCESSO', '2026-07-02T10:00:00Z'
        )
        """);
  }

  private static void migrate(String container, String network, String credential) throws Exception {
    command(
        Map.of("FLYWAY_PASSWORD", credential),
        "docker", "run", "--pull=never", "--rm", "--network", network,
        "-e", "FLYWAY_PASSWORD",
        "-v", MIGRATIONS + ":/flyway/sql:ro",
        "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/topsv3_admin_staff",
        "-user=topsv3test",
        "-locations=filesystem:/flyway/sql",
        "migrate");
  }

  private static void awaitPostgres(String container, String credential) throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandIgnoringFailure(
          Map.of("PGPASSWORD", credential),
          "docker", "exec", "-e", "PGPASSWORD", container,
          "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
          "--dbname", "topsv3_admin_staff") == 0) {
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
    if (exit != 0) throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
    return output;
  }

  private static int commandIgnoringFailure(String... args) throws Exception {
    return commandIgnoringFailure(Map.of(), args);
  }

  private static int commandIgnoringFailure(Map<String, String> environment, String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }
}
