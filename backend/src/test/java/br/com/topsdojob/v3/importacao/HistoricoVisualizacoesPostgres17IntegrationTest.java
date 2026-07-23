package br.com.topsdojob.v3.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
    named = "HISTORICO_VISUALIZACOES_POSTGRES17_ENABLED",
    matches = "true")
class HistoricoVisualizacoesPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final Path IMPORTER = Path.of(
      "..", "scripts", "local", "importacao",
      "reconciliar-historico-visualizacoes.sql").toAbsolutePath().normalize();
  private static final String DATABASE_CREDENTIAL_ENV = "PG" + "PASSWORD";
  private static final String POSTGRES_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final String SOURCE_DATABASE = "source_snapshot";
  private static final String TARGET_DATABASE = "topsv3_visualizacoes";
  private static final String SNAPSHOT_ID = "snapshot-metricas-test";
  private static final String SNAPSHOT_FINGERPRINT = "fingerprint-metricas-test";

  @Test
  void reconciliaEventosSaldoECliquesEmPostgres17ComRetryIdempotente() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-metricas-test-" + suffix + "-net";
    String container = "topsv3-metricas-test-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-metricas-test-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", network);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network,
        "-e", "POSTGRES_DB=postgres",
        "-e", "POSTGRES_USER=topsv3test",
        "-e", POSTGRES_CREDENTIAL_ENV + "=" + credential,
        "postgres:17-alpine");
    try {
      awaitPostgres(container, credential, logs);
      createDatabase(container, credential, SOURCE_DATABASE, logs);
      createDatabase(container, credential, TARGET_DATABASE, logs);
      migrate(container, network, credential, logs);
      command(true, logs.resolve("copy-importer.log"),
          "docker", "cp", IMPORTER.toString(), container + ":/tmp/reconciliar.sql");
      executeSql(container, credential, SOURCE_DATABASE, sourceSql(), logs.resolve("source.log"));
      executeSql(container, credential, TARGET_DATABASE, targetSql(), logs.resolve("target.log"));

      runImporter(container, credential, "DRY_RUN", SNAPSHOT_FINGERPRINT,
          logs.resolve("dry-run.log"), true);
      String dryRun = Files.readString(logs.resolve("dry-run.log"));
      assertThat(dryRun)
          .contains("SALDO_NEGATIVO|5|1|2|-1")
          .contains("NAO_MAPEADO_BLOQUEANTE|6|")
          .contains("FORA_DO_SNAPSHOT|7|")
          .contains("DRY_RUN|7|6|5|1|1|2|2|1|7|13|7|");
      assertThat(reconciliationState(container, credential)).isEqualTo("0|0|0|0");

      authorizeApply(container, credential, logs);
      int blocked = runImporter(
          container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-blocked.log"), false);
      assertThat(blocked).isNotZero();
      assertThat(Files.readString(logs.resolve("apply-blocked.log")))
          .contains("anuncios nao mapeados 1")
          .contains("eventos nao mapeados 0")
          .contains("cliques nao mapeados 1")
          .contains("saldos negativos 1");

      executeSql(container, credential, SOURCE_DATABASE, """
          DELETE FROM cliques_whatsapp WHERE anuncio_id IN (5, 6, 7);
          DELETE FROM anuncio_view_log WHERE anuncio_id IN (5, 6, 7);
          DELETE FROM anuncios WHERE id IN (5, 6, 7);
          """, logs.resolve("source-final.log"));

      runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-1.log"), true);
      assertThat(reconciliationState(container, credential)).isEqualTo("4|7|5|4");
      assertThat(Files.readString(logs.resolve("apply-1.log")))
          .contains("APLICAR|4|4|4|0|0|2|2|0|7|12|5|")
          .contains("|4|");

      runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-2.log"), true);
      assertThat(reconciliationState(container, credential)).isEqualTo("4|7|5|4");
      assertThat(Files.readString(logs.resolve("apply-2.log")))
          .contains("APLICAR|4|4|4|0|0|2|2|0|7|12|5|")
          .contains("|0|0|0");

      assertThat(query(
          container,
          credential,
          TARGET_DATABASE,
          "SELECT count(*) FROM agregado_visualizacao_inicial WHERE total_visualizacoes = 0",
          "^[0-9]+$")).isEqualTo("2");
      assertThat(query(
          container,
          credential,
          TARGET_DATABASE,
          dailyClicksSql(),
          "^[0-9]{4}-[0-9]{2}-[0-9]{2}:[0-9]+(,[0-9]{4}-[0-9]{2}-[0-9]{2}:[0-9]+)*$"))
          .isEqualTo("2026-07-20:2,2026-07-21:2");

      executeSql(container, credential, TARGET_DATABASE, nativeEventSql(), logs.resolve("native.log"));
      assertThat(query(
          container,
          credential,
          TARGET_DATABASE,
          canonicalTotalSql(),
          "^[0-9]+$")).isEqualTo("6");

      executeSql(container, credential, SOURCE_DATABASE,
          "UPDATE anuncios SET visualizacoes = 6 WHERE id = 3",
          logs.resolve("source-divergent.log"));
      int sourceDivergence = runImporter(
          container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("source-divergent-apply.log"), false);
      assertThat(sourceDivergence).isNotZero();
      assertThat(Files.readString(logs.resolve("source-divergent-apply.log")))
          .contains("historico ja importado diverge em saldo, fingerprint, hash ou corte temporal");
      executeSql(container, credential, SOURCE_DATABASE,
          "UPDATE anuncios SET visualizacoes = 5 WHERE id = 3",
          logs.resolve("source-restore.log"));

      assertDivergenceBlocked(
          container,
          credential,
          logs,
          """
          UPDATE agregado_visualizacao_inicial
          SET total_visualizacoes = 9
          WHERE anuncio_id = '20000000-0000-0000-0000-000000000003'
          """,
          "saldo");
      assertDivergenceBlocked(
          container,
          credential,
          logs,
          """
          UPDATE agregado_visualizacao_inicial
          SET origem_hash = repeat('0', 32)
          WHERE anuncio_id = '20000000-0000-0000-0000-000000000003'
          """,
          "hash");
      assertDivergenceBlocked(
          container,
          credential,
          logs,
          """
          UPDATE agregado_visualizacao_inicial
          SET snapshot_corte_em = snapshot_corte_em + interval '1 second'
          WHERE anuncio_id = '20000000-0000-0000-0000-000000000003'
          """,
          "corte");

      int fingerprintDivergence = runImporter(
          container,
          credential,
          "APLICAR",
          "fingerprint-divergente",
          logs.resolve("fingerprint-divergent.log"),
          false);
      assertThat(fingerprintDivergence).isNotZero();
      assertThat(Files.readString(logs.resolve("fingerprint-divergent.log")))
          .contains("snapshotId ja registrado com fingerprint divergente");
    } finally {
      command(false, logs.resolve("cleanup-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"), "docker", "network", "rm", network);
      deleteTree(logs);
    }
  }

  private static String sourceSql() {
    return """
        CREATE TABLE anuncios (
          id bigint PRIMARY KEY,
          visualizacoes bigint NOT NULL,
          criado_em timestamp without time zone NOT NULL
        );
        CREATE TABLE anuncio_view_log (
          anuncio_id bigint NOT NULL,
          id bigint PRIMARY KEY,
          usuario_id bigint NOT NULL,
          visto_em timestamp without time zone NOT NULL
        );
        CREATE TABLE cliques_whatsapp (
          anuncio_id bigint NOT NULL,
          data_clique timestamp without time zone NOT NULL,
          id bigint PRIMARY KEY,
          ip varchar(255),
          user_agent varchar(255)
        );
        INSERT INTO anuncios VALUES
          (1, 0, '2026-07-20 08:00:00'),
          (2, 3, '2026-07-20 08:10:00'),
          (3, 5, '2026-07-20 08:20:00'),
          (4, 4, '2026-07-20 08:30:00'),
          (5, 1, '2026-07-20 08:40:00'),
          (6, 2, '2026-07-20 08:50:00'),
          (7, 2, '2026-07-21 10:00:01');
        INSERT INTO anuncio_view_log VALUES
          (2, 101, 999, '2026-07-20 09:00:00'),
          (2, 102, 999, '2026-07-20 09:01:00'),
          (2, 103, 999, '2026-07-20 09:02:00'),
          (3, 104, 999, '2026-07-20 10:00:00'),
          (3, 105, 999, '2026-07-21 10:00:00'),
          (5, 106, 999, '2026-07-20 11:00:00'),
          (5, 107, 999, '2026-07-20 11:01:00');
        INSERT INTO cliques_whatsapp VALUES
          (2, '2026-07-20 12:00:00', 201, 'redacted', 'redacted'),
          (3, '2026-07-20 23:30:00', 202, 'redacted', 'redacted'),
          (3, '2026-07-21 00:15:00', 203, 'redacted', 'redacted'),
          (4, '2026-07-21 08:00:00', 204, 'redacted', 'redacted'),
          (6, '2026-07-20 13:00:00', 205, 'redacted', 'redacted');
        """;
  }

  private static String targetSql() {
    return """
        INSERT INTO usuario (
          id, nome, status, tipo_conta, criado_em, atualizado_em, versao
        ) VALUES (
          '10000000-0000-0000-0000-000000000001', 'Fixture', 'ATIVO', 'ANUNCIANTE',
          '2026-07-21T10:00:00Z', '2026-07-21T10:00:00Z', 0
        );
        INSERT INTO anuncio (
          id, usuario_id, slug, titulo, status, status_moderacao, categoria,
          criado_em, atualizado_em, versao
        )
        SELECT
          ('20000000-0000-0000-0000-' || lpad(id::text, 12, '0'))::uuid,
          '10000000-0000-0000-0000-000000000001',
          'fixture-' || id,
          'Fixture ' || id,
          'PENDENTE_REVISAO',
          'PENDENTE',
          'MASSAGENS',
          '2026-07-21T10:00:00Z',
          '2026-07-21T10:00:00Z',
          0
        FROM generate_series(1, 5) id;
        INSERT INTO importacao_execucao (
          id, sistema_origem, status, iniciado_em, resumo_json, criado_em
        ) VALUES (
          md5('dryrun:snapshot:%s')::uuid,
          'TOPSDOJOB_PRODUCAO_SNAPSHOT_READONLY',
          'CONCLUIDA_COM_PENDENCIAS',
          '2026-07-21T10:00:00Z',
          jsonb_build_object('snapshotFingerprint', '%s'),
          '2026-07-21T10:00:00Z'
        );
        INSERT INTO importacao_mapeamento (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
        )
        SELECT
          md5('map:' || id)::uuid,
          md5('dryrun:snapshot:%s')::uuid,
          'TOPSDOJOB_PRODUCAO',
          'anuncios',
          id::text,
          'ANUNCIO',
          ('20000000-0000-0000-0000-' || lpad(id::text, 12, '0'))::uuid,
          'MAPEADO',
          '2026-07-21T10:00:00Z',
          '2026-07-21T10:00:00Z'
        FROM generate_series(1, 5) id;
        """.formatted(SNAPSHOT_ID, SNAPSHOT_FINGERPRINT, SNAPSHOT_ID);
  }

  private static void authorizeApply(
      String container,
      String credential,
      Path logs) throws Exception {
    executeSql(container, credential, TARGET_DATABASE, """
        UPDATE importacao_execucao
        SET resumo_json = resumo_json ||
          jsonb_build_object('historicoVisualizacoesApplyAutorizado', 'true');
        """, logs.resolve("authorize.log"));
  }

  private static String nativeEventSql() {
    return """
        INSERT INTO evento_visualizacao (
          id, anuncio_id, request_id, criado_em
        ) VALUES (
          '40000000-0000-0000-0000-000000000001',
          '20000000-0000-0000-0000-000000000003',
          'native:test:1',
          '2026-07-21T10:00:01Z'
        );
        INSERT INTO agregado_visualizacao_diaria (
          id, anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave,
          total_visualizacoes, visitantes_estimados, atualizado_em
        ) VALUES (
          '50000000-0000-0000-0000-000000000001',
          '20000000-0000-0000-0000-000000000003',
          '2026-07-21',
          'DESCONHECIDA',
          'DESCONHECIDA',
          99,
          99,
          '2026-07-21T10:00:02Z'
        );
        """;
  }

  private static String canonicalTotalSql() {
    return """
        SELECT i.total_visualizacoes + count(e.id)
        FROM agregado_visualizacao_inicial i
        LEFT JOIN evento_visualizacao e
          ON e.anuncio_id = i.anuncio_id
         AND (
           e.request_id LIKE 'import:anuncio_view_log:%'
           OR e.criado_em > i.snapshot_corte_em
         )
        WHERE i.anuncio_id = '20000000-0000-0000-0000-000000000003'
        GROUP BY i.total_visualizacoes;
        """;
  }

  private static String dailyClicksSql() {
    return """
        SELECT string_agg(data_referencia || ':' || total, ',' ORDER BY data_referencia)
        FROM (
          SELECT
            (criado_em AT TIME ZONE 'America/Sao_Paulo')::date AS data_referencia,
            count(*) AS total
          FROM clique_whatsapp
          WHERE request_id LIKE 'import:cliques_whatsapp:%'
          GROUP BY (criado_em AT TIME ZONE 'America/Sao_Paulo')::date
        ) q;
        """;
  }

  private static void assertDivergenceBlocked(
      String container,
      String credential,
      Path logs,
      String mutation,
      String label) throws Exception {
    executeSql(
        container,
        credential,
        TARGET_DATABASE,
        mutation,
        logs.resolve(label + "-setup.log"));
    int exit = runImporter(
        container,
        credential,
        "APLICAR",
        SNAPSHOT_FINGERPRINT,
        logs.resolve(label + "-blocked.log"),
        false);
    assertThat(exit).isNotZero();
    assertThat(Files.readString(logs.resolve(label + "-blocked.log")))
        .contains("historico ja importado diverge em saldo, fingerprint, hash ou corte temporal");
    executeSql(
        container,
        credential,
        TARGET_DATABASE,
        restoreAggregateSql(),
        logs.resolve(label + "-restore.log"));
  }

  private static String restoreAggregateSql() {
    return """
        UPDATE agregado_visualizacao_inicial
        SET total_visualizacoes = 3,
            origem_hash = md5(
              'TOPSDOJOB_PRODUCAO|anuncios|3|visualizacoes|5|eventos|2'
              || '|eventosHash|'
              || md5(
                '104:2026-07-20 10:00:00'
                || '|105:2026-07-21 10:00:00'
              )
            ),
            snapshot_corte_em = '2026-07-21T10:00:00Z'
        WHERE anuncio_id = '20000000-0000-0000-0000-000000000003';
        """;
  }

  private static String reconciliationState(String container, String credential) throws Exception {
    return query(container, credential, TARGET_DATABASE, """
        SELECT concat_ws('|',
          (SELECT count(*) FROM agregado_visualizacao_inicial),
          (SELECT coalesce(sum(total_visualizacoes), 0)
           FROM agregado_visualizacao_inicial),
          (SELECT count(*) FROM evento_visualizacao
           WHERE request_id LIKE 'import:anuncio_view_log:%'),
          (SELECT count(*) FROM clique_whatsapp
           WHERE request_id LIKE 'import:cliques_whatsapp:%'));
        """, "^[0-9]+\\|[0-9]+\\|[0-9]+\\|[0-9]+$");
  }

  private static int runImporter(
      String container,
      String credential,
      String mode,
      String fingerprint,
      Path log,
      boolean check) throws Exception {
    return command(check, log,
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3test",
        "--dbname", TARGET_DATABASE, "--tuples-only", "--no-align",
        "--field-separator=|", "--set", "ON_ERROR_STOP=1",
        "--set", "snapshot_id=" + SNAPSHOT_ID,
        "--set", "snapshot_fingerprint=" + fingerprint,
        "--set", "modo=" + mode,
        "--file", "/tmp/reconciliar.sql");
  }

  private static void createDatabase(
      String container,
      String credential,
      String database,
      Path logs) throws Exception {
    command(true, logs.resolve("createdb-" + database + ".log"),
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "createdb", "--host", "127.0.0.1", "--username", "topsv3test",
        "--maintenance-db", "postgres", database);
  }

  private static void migrate(
      String container,
      String network,
      String credential,
      Path logs) throws Exception {
    command(true, logs.resolve("flyway.log"),
        "docker", "run", "--pull=never", "--rm", "--network", network,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + TARGET_DATABASE,
        "-user=topsv3test", FLYWAY_CREDENTIAL_OPTION + credential,
        "-locations=filesystem:/flyway/sql", "migrate");
  }

  private static void awaitPostgres(
      String container,
      String credential,
      Path logs) throws Exception {
    for (int attempt = 0; attempt < 90; attempt++) {
      int exit = command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
          "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
          "--dbname", "postgres");
      if (exit == 0) {
        return;
      }
      Thread.sleep(1_000L);
    }
    throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
  }

  private static void executeSql(
      String container,
      String credential,
      String database,
      String sql,
      Path log) throws Exception {
    command(true, log,
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3test",
        "--dbname", database, "--set", "ON_ERROR_STOP=1", "--command", sql);
  }

  private static String query(
      String container,
      String credential,
      String database,
      String sql,
      String expectedPattern) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3test",
        "--dbname", database, "--tuples-only", "--no-align",
        "--set", "ON_ERROR_STOP=1", "--command", sql);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException("consulta PostgreSQL 17 falhou");
    }
    return output.lines()
        .map(String::trim)
        .filter(line -> line.matches(expectedPattern))
        .reduce((first, second) -> second)
        .orElseThrow(() -> new IllegalStateException("resultado PostgreSQL 17 ausente"));
  }

  private static int command(boolean check, Path output, String... arguments) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(List.of(arguments)));
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (check && exit != 0) {
      String detail = Files.readString(output).lines()
          .filter(line -> line.contains("ERROR:") || line.startsWith("psql:"))
          .reduce((first, second) -> second)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("comando PostgreSQL 17 falhou: " + detail);
    }
    return exit;
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
