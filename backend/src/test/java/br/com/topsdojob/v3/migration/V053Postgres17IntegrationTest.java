package br.com.topsdojob.v3.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "V053_POSTGRES17_ENABLED", matches = "true")
class V053Postgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();

  @Test
  void upgradeDe052PreservaLegadoEReleaseAnteriorContinuaEscrevendo() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-v053-" + suffix + "-net";
    String container = "topsv3-v053-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    UUID beforeMigration = UUID.randomUUID();
    UUID oldReleaseAfterMigration = UUID.randomUUID();

    command("docker", "network", "create", network);
    command(
        Map.of("POSTGRES_PASSWORD", credential),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network,
        "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=topsv3_v053",
        "-e", "POSTGRES_USER=topsv3test",
        "-e", "POSTGRES_PASSWORD",
        "postgres:17-alpine");
    try {
      awaitPostgres(container, credential);
      flyway(container, network, credential, "-target=52", "migrate");
      JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + mappedPort(container) + "/topsv3_v053",
          "topsv3test",
          credential));
      insertUsingV052Shape(jdbc, beforeMigration, "antes.jpg");

      flyway(container, network, credential, "migrate");
      flyway(container, network, credential, "validate");
      insertUsingV052Shape(jdbc, oldReleaseAfterMigration, "depois.jpg");

      assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class))
          .isBetween(170000, 179999);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM flyway_schema_history WHERE version::integer = 53 AND success",
          Long.class)).isEqualTo(1L);
      assertThat(jdbc.queryForList(
          "SELECT preview_restrito_status FROM arquivo_midia WHERE id IN (?, ?) ORDER BY id",
          String.class,
          beforeMigration,
          oldReleaseAfterMigration)).containsExactly("DESCONHECIDO", "DESCONHECIDO");
      assertThat(jdbc.queryForObject("""
          SELECT count(*)
          FROM information_schema.columns
          WHERE table_schema = 'public'
            AND table_name = 'arquivo_midia'
            AND column_name LIKE 'preview_restrito_%'
            AND is_nullable = 'YES'
          """, Long.class)).isEqualTo(5L);

      assertThatThrownBy(() -> jdbc.update("""
          UPDATE arquivo_midia
          SET preview_restrito_status = 'DISPONIVEL'
          WHERE id = ?
          """, beforeMigration))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
          .hasRootCauseInstanceOf(SQLException.class);

      Map<String, Object> antesDoBackfill = jdbc.queryForMap("""
          SELECT storage_provider, bucket, chave_objeto, nome_original, mime_type,
                 tamanho_bytes, largura, altura, sha256, status_arquivo, criado_em
          FROM arquivo_midia
          WHERE id = ?
          """, beforeMigration);
      PreviewRestritoBackfillJdbcRepository backfill =
          new PreviewRestritoBackfillJdbcRepository(jdbc);
      var atualizacao = new PreviewRestritoBackfillJdbcRepository.Atualizacao(
          beforeMigration,
          "publicas/restritas-borradas/v1/prova.jpg",
          "v1",
          OffsetDateTime.now());

      assertThat(backfill.marcarDisponiveis(List.of(atualizacao))).isEqualTo(1);
      assertThat(backfill.marcarDisponiveis(List.of(atualizacao))).isZero();
      assertThat(jdbc.queryForObject(
          "SELECT preview_restrito_status FROM arquivo_midia WHERE id = ?",
          String.class,
          beforeMigration)).isEqualTo("DISPONIVEL");
      assertThat(jdbc.queryForMap("""
          SELECT storage_provider, bucket, chave_objeto, nome_original, mime_type,
                 tamanho_bytes, largura, altura, sha256, status_arquivo, criado_em
          FROM arquivo_midia
          WHERE id = ?
          """, beforeMigration)).isEqualTo(antesDoBackfill);
    } finally {
      commandIgnoringFailure("docker", "rm", "-f", container);
      commandIgnoringFailure("docker", "network", "rm", network);
    }
  }

  private static void insertUsingV052Shape(JdbcTemplate jdbc, UUID id, String key) {
    jdbc.update("""
        INSERT INTO arquivo_midia (
          id, storage_provider, bucket, chave_objeto, nome_original, mime_type,
          tamanho_bytes, largura, altura, sha256, status_arquivo, criado_em
        ) VALUES (?, 'R2', 'privado', ?, 'foto.jpg', 'image/jpeg',
                  10, 100, 100, ?, 'VALIDADO', now())
        """, id, key, "a".repeat(64));
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
        "-url=jdbc:postgresql://" + container + ":5432/topsv3_v053",
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
          "--username", "topsv3test", "--dbname", "topsv3_v053") == 0) {
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
