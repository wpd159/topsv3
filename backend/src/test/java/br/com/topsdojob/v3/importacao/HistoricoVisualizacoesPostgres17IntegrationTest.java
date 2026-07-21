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
  private static final Path IMPORTADOR = Path.of(
      "..", "scripts", "local", "importacao",
      "reconciliar-historico-visualizacoes.sql").toAbsolutePath().normalize();
  private static final String DATABASE_CREDENTIAL_ENV = "PG" + "PASSWORD";
  private static final String POSTGRES_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final String SNAPSHOT_ID = "snapshot-visualizacoes-test";
  private static final String SNAPSHOT_FINGERPRINT = "fingerprint-visualizacoes-test";

  @Test
  void reconciliaEmPostgres17SemDuplicarOuAceitarDivergencias() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-visualizacoes-test-" + suffix + "-net";
    String container = "topsv3-visualizacoes-test-" + suffix + "-pg17";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-visualizacoes-test-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", network);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network,
        "-e", "POSTGRES_DB=topsv3_visualizacoes",
        "-e", "POSTGRES_USER=topsv3test",
        "-e", POSTGRES_CREDENTIAL_ENV + "=" + credential,
        "postgres:17-alpine");
    try {
      awaitPostgres(container, credential, logs);
      migrate(container, network, credential, logs);
      command(true, logs.resolve("copy-importer.log"),
          "docker", "cp", IMPORTADOR.toString(), container + ":/tmp/reconciliar.sql");
      executeSql(container, credential, setupSql(), logs.resolve("setup.log"));

      runImporter(container, credential, "DRY_RUN", SNAPSHOT_FINGERPRINT,
          logs.resolve("dry-run.log"), true);
      assertThat(Files.readString(logs.resolve("dry-run.log")))
          .contains("FORA_DO_SNAPSHOT|3|")
          .contains("DRY_RUN|3|2|2|1|0|8|8|2|10|0|0");
      assertThat(query(container, credential,
          "SELECT count(*) FROM agregado_visualizacao_inicial", "^[0-9]+$"))
          .isEqualTo("0");

      executeSql(container, credential, """
          UPDATE importacao_execucao
          SET resumo_json = resumo_json ||
            jsonb_build_object('historicoVisualizacoesApplyAutorizado', 'true');
          """, logs.resolve("authorize-final-snapshot.log"));
      int outsideSnapshotExit = runImporter(
          container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-outside-snapshot.log"), false);
      assertThat(outsideSnapshotExit).isNotZero();
      assertThat(Files.readString(logs.resolve("apply-outside-snapshot.log")))
          .contains("APPLY proibido: fora do snapshot 1, nao mapeados bloqueantes 0, autorizacao t");

      executeSql(container, credential, """
          INSERT INTO legacy.anuncios VALUES (4, 5, '2026-07-21 08:45:00');
          """, logs.resolve("blocking-source.log"));
      runImporter(container, credential, "DRY_RUN", SNAPSHOT_FINGERPRINT,
          logs.resolve("blocking-dry-run.log"), true);
      assertThat(Files.readString(logs.resolve("blocking-dry-run.log")))
          .contains("NAO_MAPEADO_BLOQUEANTE|4|")
          .contains("DRY_RUN|4|3|2|1|1|13|8|2|15|0|0");

      int blockedExit = runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-blocked.log"), false);
      assertThat(blockedExit).isNotZero();
      assertThat(Files.readString(logs.resolve("apply-blocked.log")))
          .contains("APPLY proibido: fora do snapshot 1, nao mapeados bloqueantes 1, autorizacao t");

      executeSql(container, credential, """
          DELETE FROM legacy.anuncios WHERE id IN (3, 4);
          """, logs.resolve("final-snapshot.log"));
      runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-1.log"), true);
      assertThat(reconciliationState(container, credential)).isEqualTo("2|8");
      assertThat(Files.readString(logs.resolve("apply-1.log")))
          .contains("APLICAR|2|2|2|0|0|8|8|0|8|2|0");

      runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("apply-2.log"), true);
      assertThat(reconciliationState(container, credential)).isEqualTo("2|8");
      assertThat(Files.readString(logs.resolve("apply-2.log")))
          .contains("APLICAR|2|2|2|0|0|8|8|0|8|0|2");

      executeSql(container, credential,
          "UPDATE legacy.anuncios SET visualizacoes = 9 WHERE id = 2",
          logs.resolve("source-total-divergente-setup.log"));
      int sourceTotalExit = runImporter(
          container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
          logs.resolve("source-total-divergente.log"), false);
      assertThat(sourceTotalExit).isNotZero();
      assertThat(Files.readString(logs.resolve("source-total-divergente.log")))
          .contains("historico ja importado diverge em total, fingerprint, hash ou corte temporal");
      assertThat(reconciliationState(container, credential)).isEqualTo("2|8");
      executeSql(container, credential,
          "UPDATE legacy.anuncios SET visualizacoes = 8 WHERE id = 2",
          logs.resolve("source-total-divergente-restore.log"));

      executeSql(container, credential, eventsSql(), logs.resolve("events.log"));
      assertThat(query(container, credential, canonicalTotalSql(), "^[0-9]+$")).isEqualTo("10");
      assertThat(query(container, credential, isolationSql(), "^[0-9]+\\|[0-9]+$"))
          .isEqualTo("0|0");

      assertDivergenceBlocked(container, credential, logs,
          "UPDATE agregado_visualizacao_inicial SET total_visualizacoes = 9",
          "total-divergente");
      assertDivergenceBlocked(container, credential, logs,
          "UPDATE agregado_visualizacao_inicial SET origem_hash = repeat('0', 32)",
          "hash-divergente");
      assertDivergenceBlocked(container, credential, logs,
          "UPDATE agregado_visualizacao_inicial SET snapshot_corte_em = snapshot_corte_em + interval '1 second'",
          "corte-divergente");

      int fingerprintExit = runImporter(
          container, credential, "APLICAR", "fingerprint-divergente",
          logs.resolve("fingerprint-divergente.log"), false);
      assertThat(fingerprintExit).isNotZero();
      assertThat(Files.readString(logs.resolve("fingerprint-divergente.log")))
          .contains("snapshotId ja registrado com fingerprint divergente");
    } finally {
      command(false, logs.resolve("cleanup-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"), "docker", "network", "rm", network);
      deleteTree(logs);
    }
  }

  private static String setupSql() {
    return """
        CREATE SCHEMA legacy;
        CREATE TABLE legacy.anuncios (
          id bigint PRIMARY KEY,
          visualizacoes bigint,
          criado_em timestamp without time zone NOT NULL
        );
        INSERT INTO legacy.anuncios VALUES
          (1, 0, '2026-07-21 08:00:00'),
          (2, 8, '2026-07-21 08:30:00'),
          (3, 2, '2026-07-21 10:00:00');

        INSERT INTO usuario (
          id, nome, status, tipo_conta, criado_em, atualizado_em, versao
        ) VALUES (
          '10000000-0000-0000-0000-000000000001', 'Fixture', 'ATIVO', 'ANUNCIANTE',
          '2026-07-21T12:00:00Z', '2026-07-21T12:00:00Z', 0
        );

        INSERT INTO anuncio (
          id, usuario_id, slug, titulo, status, status_moderacao, categoria,
          criado_em, atualizado_em, versao
        ) VALUES
          ('20000000-0000-0000-0000-000000000001',
           '10000000-0000-0000-0000-000000000001', 'fixture-1', 'Fixture 1',
           'PUBLICADO', 'APROVADO', 'MASSAGENS',
           '2026-07-21T12:00:00Z', '2026-07-21T12:00:00Z', 0),
          ('20000000-0000-0000-0000-000000000002',
           '10000000-0000-0000-0000-000000000001', 'fixture-2', 'Fixture 2',
           'PUBLICADO', 'APROVADO', 'MASSAGENS',
           '2026-07-21T12:00:00Z', '2026-07-21T12:00:00Z', 0);

        INSERT INTO importacao_execucao (
          id, sistema_origem, status, iniciado_em, resumo_json, criado_em
        ) VALUES (
          md5('dryrun:snapshot:%s')::uuid,
          'TOPSDOJOB_PRODUCAO', 'CONCLUIDA_COM_PENDENCIAS',
          '2026-07-21T12:00:00Z',
          jsonb_build_object('snapshotFingerprint', '%s'),
          '2026-07-21T12:00:00Z'
        );

        INSERT INTO importacao_mapeamento (
          id, execucao_id, sistema_origem, tabela_origem, id_origem,
          entidade_tipo, entidade_v3_id, status, criado_em, atualizado_em
        ) VALUES
          ('30000000-0000-0000-0000-000000000001',
           md5('dryrun:snapshot:%s')::uuid, 'TOPSDOJOB_PRODUCAO', 'anuncios', '1',
           'ANUNCIO', '20000000-0000-0000-0000-000000000001', 'MAPEADO',
           '2026-07-21T12:00:00Z', '2026-07-21T12:00:00Z'),
          ('30000000-0000-0000-0000-000000000002',
           md5('dryrun:snapshot:%s')::uuid, 'TOPSDOJOB_PRODUCAO', 'anuncios', '2',
           'ANUNCIO', '20000000-0000-0000-0000-000000000002', 'MAPEADO',
           '2026-07-21T12:00:00Z', '2026-07-21T12:00:00Z');
        """.formatted(
        SNAPSHOT_ID, SNAPSHOT_FINGERPRINT, SNAPSHOT_ID, SNAPSHOT_ID);
  }

  private static String eventsSql() {
    return """
        INSERT INTO evento_visualizacao (id, anuncio_id, criado_em) VALUES
          ('40000000-0000-0000-0000-000000000001',
           '20000000-0000-0000-0000-000000000002', '2026-07-21T11:59:59Z'),
          ('40000000-0000-0000-0000-000000000002',
           '20000000-0000-0000-0000-000000000002', '2026-07-21T12:00:00Z'),
          ('40000000-0000-0000-0000-000000000003',
           '20000000-0000-0000-0000-000000000002', '2026-07-21T12:00:01Z'),
          ('40000000-0000-0000-0000-000000000004',
           '20000000-0000-0000-0000-000000000002', '2026-07-21T12:00:02Z');

        INSERT INTO agregado_visualizacao_diaria (
          id, anuncio_id, data_referencia, origem_uf_chave, origem_cidade_chave,
          total_visualizacoes, visitantes_estimados, atualizado_em
        ) VALUES (
          '50000000-0000-0000-0000-000000000001',
          '20000000-0000-0000-0000-000000000002', '2026-07-21',
          'DESCONHECIDA', 'DESCONHECIDA', 2, 2, '2026-07-21T12:01:00Z'
        );
        """;
  }

  private static String canonicalTotalSql() {
    return """
        SELECT i.total_visualizacoes + (
          SELECT count(*)
          FROM evento_visualizacao e
          WHERE e.anuncio_id = i.anuncio_id
            AND e.criado_em > i.snapshot_corte_em
        )
        FROM agregado_visualizacao_inicial i
        WHERE i.anuncio_id = '20000000-0000-0000-0000-000000000002';
        """;
  }

  private static String isolationSql() {
    return """
        SELECT concat_ws('|',
          (SELECT count(*) FROM clique_whatsapp),
          (SELECT count(*) FROM agregado_clique_whatsapp_diario));
        """;
  }

  private static void assertDivergenceBlocked(
      String container,
      String credential,
      Path logs,
      String mutation,
      String label) throws Exception {
    executeSql(container, credential, mutation, logs.resolve(label + "-setup.log"));
    int exit = runImporter(container, credential, "APLICAR", SNAPSHOT_FINGERPRINT,
        logs.resolve(label + ".log"), false);
    assertThat(exit).isNotZero();
    assertThat(Files.readString(logs.resolve(label + ".log")))
        .contains("historico ja importado diverge em total, fingerprint, hash ou corte temporal");
    executeSql(container, credential, """
        UPDATE agregado_visualizacao_inicial i
        SET total_visualizacoes = o.visualizacoes,
            origem_hash = md5(
              'TOPSDOJOB_PRODUCAO|anuncios|' || o.id::text
              || '|visualizacoes|' || o.visualizacoes::text
            ),
            snapshot_corte_em = e.iniciado_em
        FROM legacy.anuncios o
        JOIN importacao_mapeamento m
          ON m.tabela_origem = 'anuncios'
         AND m.id_origem = o.id::text
         AND m.entidade_tipo = 'ANUNCIO'
         AND m.status = 'MAPEADO'
        JOIN importacao_execucao e ON e.id = m.execucao_id
        WHERE i.anuncio_id = m.entidade_v3_id
          AND e.id = i.execucao_id;
        """, logs.resolve(label + "-restore.log"));
  }

  private static String reconciliationState(String container, String credential) throws Exception {
    return query(container, credential, """
        SELECT concat_ws('|', count(*), coalesce(sum(total_visualizacoes), 0))
        FROM agregado_visualizacao_inicial;
        """, "^[0-9]+\\|[0-9]+$");
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
        "--dbname", "topsv3_visualizacoes", "--tuples-only", "--no-align",
        "--field-separator=|", "--set", "ON_ERROR_STOP=1",
        "--set", "snapshot_id=" + SNAPSHOT_ID,
        "--set", "snapshot_fingerprint=" + fingerprint,
        "--set", "modo=" + mode,
        "--file", "/tmp/reconciliar.sql");
  }

  private static void migrate(
      String container,
      String network,
      String credential,
      Path logs) throws Exception {
    command(true, logs.resolve("flyway.log"),
        "docker", "run", "--pull=never", "--rm", "--network", network,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/topsv3_visualizacoes",
        "-user=topsv3test", FLYWAY_CREDENTIAL_OPTION + credential,
        "-locations=filesystem:/flyway/sql", "migrate");
  }

  private static void awaitPostgres(String container, String credential, Path logs)
      throws Exception {
    for (int attempt = 0; attempt < 90; attempt++) {
      int exit = command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
          "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
          "--dbname", "topsv3_visualizacoes");
      if (exit == 0) return;
      Thread.sleep(1_000L);
    }
    throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
  }

  private static void executeSql(
      String container,
      String credential,
      String sql,
      Path log) throws Exception {
    command(true, log,
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3test",
        "--dbname", "topsv3_visualizacoes", "--set", "ON_ERROR_STOP=1",
        "--command", sql);
  }

  private static String query(
      String container,
      String credential,
      String sql,
      String expectedPattern) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + credential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3test",
        "--dbname", "topsv3_visualizacoes", "--tuples-only", "--no-align",
        "--set", "ON_ERROR_STOP=1", "--command", sql);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) throw new IllegalStateException("Consulta PostgreSQL 17 falhou");
    return output.lines()
        .map(String::trim)
        .filter(line -> line.matches(expectedPattern))
        .reduce((first, second) -> second)
        .orElseThrow(() -> new IllegalStateException("Resultado PostgreSQL 17 ausente"));
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
      throw new IllegalStateException("Comando PostgreSQL 17 falhou: " + detail);
    }
    return exit;
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
