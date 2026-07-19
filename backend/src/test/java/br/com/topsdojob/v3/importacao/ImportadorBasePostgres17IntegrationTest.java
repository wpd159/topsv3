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
    named = "IMPORTADOR_BASE_IDEMPOTENCIA_ENABLED",
    matches = "true")
class ImportadorBasePostgres17IntegrationTest {

  private static final Path IMPORTACAO = Path.of(
      "..", "scripts", "local", "importacao").toAbsolutePath().normalize();
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String POSTGRES_CONTAINER_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String DATABASE_CREDENTIAL_ENV = "PG" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";

  @Test
  void executaMesmoSnapshotDuasVezesSemAlterarContagensOuFingerprint() throws Exception {
    Path snapshot = requiredFile("IMPORTADOR_BASE_SNAPSHOT_DUMP");
    Path publicMedia = requiredFile("IMPORTADOR_BASE_PUBLIC_MEDIA_TSV");
    Path kycDocuments = requiredFile("IMPORTADOR_BASE_KYC_TSV");
    Path importer = IMPORTACAO.resolve("dryrun-producao-v3-saneado.sql");
    Path ledger = IMPORTACAO.resolve("reconciliar-ledger-saldo-inicial.sql");
    assertThat(importer).isRegularFile();
    assertThat(ledger).isRegularFile();

    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-idempotencia-test-" + suffix + "-net";
    String container = "topsv3-idempotencia-test-" + suffix + "-pg17";
    String dbCredential = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-importador-idempotencia-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", network);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", network, "-e", "POSTGRES_DB=postgres",
        "-e", "POSTGRES_USER=topsv3dry",
        "-e", POSTGRES_CONTAINER_CREDENTIAL_ENV + "=" + dbCredential, "postgres:17-alpine");
    try {
      awaitPostgres(container, dbCredential, logs);
      createDatabases(container, dbCredential, logs);
      restoreSnapshot(container, dbCredential, snapshot, logs);
      migrate(container, network, dbCredential, logs);
      copy(container, importer, "/tmp/dryrun-producao-v3-saneado.sql", logs);
      copy(container, ledger, "/tmp/reconciliar-ledger-saldo-inicial.sql", logs);
      copy(container, publicMedia, "/tmp/dryrun-r2-public-media.tsv", logs);
      copy(container, kycDocuments, "/tmp/dryrun-r2-kyc-documents.tsv", logs);

      List<String> variables = importVariables();
      List<String> missingPublicPrefix = withoutSetting(
          variables, "r2_public_media_prefix=");
      Path missingParameterLog = logs.resolve("parametro-ausente.log");
      int missingParameterExit = importSnapshot(
          container, dbCredential, missingPublicPrefix, missingParameterLog, false);
      assertThat(missingParameterExit).isNotZero();
      assertThat(Files.readString(missingParameterLog))
          .contains("parametro R2 obrigatorio ausente: r2_public_media_prefix");

      importSnapshot(container, dbCredential, variables, logs.resolve("run1.log"), true);
      String counts1 = counts(container, dbCredential);
      String fingerprint1 = fingerprint(container, dbCredential);

      importSnapshot(container, dbCredential, variables, logs.resolve("run2.log"), true);
      String counts2 = counts(container, dbCredential);
      String fingerprint2 = fingerprint(container, dbCredential);

      assertThat(counts2).isEqualTo(counts1);
      assertThat(fingerprint2).isEqualTo(fingerprint1);
      assertThat(counts2).startsWith("1|CONCLUIDA_COM_PENDENCIAS|");
      assertDestinationStorage(container, dbCredential, variables);

      List<String> otherEnvironment = replacingSetting(
          variables,
          "r2_public_media_prefix=",
          "r2_public_media_prefix=hml/outro-ambiente/midias-aprovadas/");
      Path otherEnvironmentLog = logs.resolve("prefixo-outro-ambiente.log");
      int otherEnvironmentExit = importSnapshot(
          container, dbCredential, otherEnvironment, otherEnvironmentLog, false);
      assertThat(otherEnvironmentExit).isNotZero();
      assertThat(Files.readString(otherEnvironmentLog))
          .contains("manifesto R2 publico nao corresponde ao prefixo e objetos do destino configurado");
      cleanupImportScaffolding(container, dbCredential, logs);

      List<String> mismatched = new ArrayList<>(variables);
      int fingerprintIndex = indexOfPrefix(mismatched, "snapshot_fingerprint=");
      mismatched.set(fingerprintIndex, "snapshot_fingerprint=fingerprint-diferente");
      Path mismatchLog = logs.resolve("snapshot-diferente.log");
      int mismatchExit = importSnapshot(
          container, dbCredential, mismatched, mismatchLog, false);
      assertThat(mismatchExit).isNotZero();
      assertThat(Files.readString(mismatchLog))
          .contains("execucao existente nao corresponde integralmente ao snapshot solicitado");
    } finally {
      command(false, logs.resolve("cleanup-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"), "docker", "network", "rm", network);
      deleteTree(logs);
    }
  }

  private static List<String> importVariables() {
    return new ArrayList<>(List.of(
        "--set", "snapshot_at=" + required("IMPORTADOR_BASE_SNAPSHOT_AT"),
        "--set", "snapshot_id=" + required("IMPORTADOR_BASE_SNAPSHOT_ID"),
        "--set", "snapshot_fingerprint=" + required("IMPORTADOR_BASE_SNAPSHOT_FINGERPRINT"),
        "--set", "r2_public_media_bucket="
            + required("IMPORTADOR_BASE_R2_PUBLIC_MEDIA_BUCKET"),
        "--set", "r2_public_media_prefix="
            + required("IMPORTADOR_BASE_R2_PUBLIC_MEDIA_PREFIX"),
        "--set", "r2_private_media_bucket="
            + required("IMPORTADOR_BASE_R2_PRIVATE_MEDIA_BUCKET"),
        "--set", "r2_private_media_prefix="
            + required("IMPORTADOR_BASE_R2_PRIVATE_MEDIA_PREFIX"),
        "--set", "r2_preserved_public_bucket="
            + required("IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_BUCKET"),
        "--set", "r2_preserved_public_base_url="
            + required("IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_BASE_URL"),
        "--set", "r2_preserved_public_prefix="
            + required("IMPORTADOR_BASE_R2_PRESERVED_PUBLIC_PREFIX"),
        "--set", "r2_document_bucket=" + required("IMPORTADOR_BASE_R2_DOCUMENT_BUCKET"),
        "--set", "r2_document_prefix="
            + required("IMPORTADOR_BASE_R2_DOCUMENT_PREFIX")));
  }

  private static void assertDestinationStorage(
      String container, String dbCredential, List<String> variables) throws Exception {
    String publicBucket = setting(variables, "r2_public_media_bucket=");
    String publicPrefix = setting(variables, "r2_public_media_prefix=");
    String sourceBucket = setting(variables, "r2_preserved_public_bucket=");
    String documentBucket = setting(variables, "r2_document_bucket=");
    String documentPrefix = setting(variables, "r2_document_prefix=");
    String result = query(container, dbCredential, """
        SELECT concat_ws('|',
          (SELECT count(*) FROM arquivo_midia
           WHERE storage_provider = 'R2'
             AND bucket = %s
             AND chave_objeto LIKE %s),
          (SELECT count(*) FROM anuncio_midia am
           JOIN arquivo_midia ar ON ar.id = am.arquivo_midia_id
           WHERE am.visibilidade_midia = 'LIVRE'
             AND ar.bucket = %s
             AND ar.chave_objeto LIKE %s),
          (SELECT count(*) FROM arquivo_midia
           WHERE storage_provider = 'R2' AND bucket = %s),
          (SELECT count(*) FROM documento_usuario d
           JOIN arquivo_midia ar ON ar.id = d.arquivo_midia_id
           WHERE ar.bucket = %s AND ar.chave_objeto LIKE %s),
          (SELECT count(*) FROM documento_usuario d
           JOIN arquivo_midia ar ON ar.id = d.arquivo_midia_id
           WHERE ar.bucket <> %s OR ar.chave_objeto NOT LIKE %s),
          (SELECT count(*) FROM arquivo_midia
           WHERE chave_objeto ~ '^https?://'));
        """.formatted(
            sqlLiteral(publicBucket), sqlLiteral(publicPrefix + "importacao/sha256/%"),
            sqlLiteral(publicBucket), sqlLiteral(publicPrefix + "importacao/sha256/%"),
            sqlLiteral(sourceBucket),
            sqlLiteral(documentBucket), sqlLiteral(documentPrefix + "importacao/%/sha256/%"),
            sqlLiteral(documentBucket), sqlLiteral(documentPrefix + "importacao/%/sha256/%")),
        "^[0-9]+\\|[0-9]+\\|[0-9]+\\|[0-9]+\\|[0-9]+\\|[0-9]+$");
    String[] values = result.split("\\|");
    assertThat(Long.parseLong(values[0])).isPositive();
    assertThat(Long.parseLong(values[1])).isPositive();
    assertThat(Long.parseLong(values[2])).isZero();
    assertThat(Long.parseLong(values[3])).isPositive();
    assertThat(Long.parseLong(values[4])).isZero();
    assertThat(Long.parseLong(values[5])).isZero();
  }

  private static void awaitPostgres(String container, String dbCredential, Path logs)
      throws Exception {
    for (int attempt = 0; attempt < 90; attempt++) {
      int exit = command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
          "pg_isready", "--host", "127.0.0.1", "--username", "topsv3dry",
          "--dbname", "postgres");
      if (exit == 0) return;
      Thread.sleep(1_000L);
    }
    throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
  }

  private static void createDatabases(String container, String dbCredential, Path logs)
      throws Exception {
    for (String database : List.of("source_snapshot", "v3_dryrun")) {
      command(true, logs.resolve("createdb-" + database + ".log"),
          "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
          "createdb", "--host", "127.0.0.1", "--username", "topsv3dry",
          "--maintenance-db", "postgres", database);
    }
  }

  private static void restoreSnapshot(
      String container, String dbCredential, Path snapshot, Path logs) throws Exception {
    copy(container, snapshot, "/tmp/source-snapshot.dump", logs);
    for (String section : List.of("pre-data", "data")) {
      command(true, logs.resolve("restore-" + section + ".log"),
          "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
          "pg_restore", "--host", "127.0.0.1", "--username", "topsv3dry",
          "--dbname", "source_snapshot", "--section=" + section, "--exit-on-error",
          "--no-owner", "--no-privileges", "/tmp/source-snapshot.dump");
    }
  }

  private static void migrate(
      String container, String network, String dbCredential, Path logs) throws Exception {
    command(true, logs.resolve("flyway.log"),
        "docker", "run", "--pull=never", "--rm", "--network", network,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/v3_dryrun",
        "-user=topsv3dry", FLYWAY_CREDENTIAL_OPTION + dbCredential,
        "-locations=filesystem:/flyway/sql", "migrate");
  }

  private static void copy(String container, Path source, String target, Path logs)
      throws Exception {
    command(true, logs.resolve("copy-" + Path.of(target).getFileName() + ".log"),
        "docker", "cp", source.toString(), container + ":" + target);
  }

  private static int importSnapshot(
      String container,
      String dbCredential,
      List<String> variables,
      Path log,
      boolean check) throws Exception {
    List<String> arguments = new ArrayList<>(List.of(
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3dry",
        "--dbname", "v3_dryrun", "--set", "ON_ERROR_STOP=1"));
    arguments.addAll(variables);
    arguments.addAll(List.of("--file", "/tmp/dryrun-producao-v3-saneado.sql"));
    return command(check, log, arguments.toArray(String[]::new));
  }

  private static void cleanupImportScaffolding(
      String container, String dbCredential, Path logs) throws Exception {
    command(true, logs.resolve("cleanup-import-scaffolding.log"),
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3dry",
        "--dbname", "v3_dryrun", "--set", "ON_ERROR_STOP=1", "--command",
        "DROP SCHEMA IF EXISTS legacy CASCADE; "
            + "DROP SERVER IF EXISTS legacy_source CASCADE; "
            + "DROP EXTENSION IF EXISTS postgres_fdw;");
  }

  private static String counts(String container, String dbCredential) throws Exception {
    return query(container, dbCredential, """
        SELECT concat_ws('|',
          (SELECT count(*) FROM importacao_execucao),
          (SELECT status FROM importacao_execucao LIMIT 1),
          (SELECT count(*) FROM usuario),
          (SELECT count(*) FROM anuncio),
          (SELECT count(*) FROM arquivo_midia),
          (SELECT count(*) FROM anuncio_midia),
          (SELECT count(*) FROM favorito_anuncio),
          (SELECT count(*) FROM importacao_mapeamento));
        """, "^[0-9]+\\|[A-Z_]+\\|.*$");
  }

  private static String fingerprint(String container, String dbCredential) throws Exception {
    return query(container, dbCredential, """
        CREATE TEMP TABLE fp(nome text primary key, total bigint, conteudo_md5 text);
        DO $body$
        DECLARE r record; n bigint; h text;
        BEGIN
          FOR r IN
            SELECT tablename FROM pg_tables
            WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
            ORDER BY tablename
          LOOP
            EXECUTE format(
              'SELECT count(*), md5(coalesce(string_agg(row_hash, '''' ORDER BY row_hash), '''')) '
              'FROM (SELECT md5(to_jsonb(t)::text) AS row_hash FROM %I t) x',
              r.tablename
            ) INTO n, h;
            INSERT INTO fp VALUES (r.tablename, n, h);
          END LOOP;
        END $body$;
        SELECT md5(coalesce(string_agg(nome || ':' || total || ':' || conteudo_md5,
          '|' ORDER BY nome), '')) FROM fp;
        """, "^[0-9a-f]{32}$");
  }

  private static String query(
      String container, String dbCredential, String sql, String expectedPattern) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(
        "docker", "exec", "-e", DATABASE_CREDENTIAL_ENV + "=" + dbCredential, container,
        "psql", "--no-psqlrc", "--host", "127.0.0.1", "--username", "topsv3dry",
        "--dbname", "v3_dryrun", "--tuples-only", "--no-align",
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
    ProcessBuilder builder = new ProcessBuilder(arguments);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (check && exit != 0) {
      String detail = Files.readString(output).lines()
          .filter(line -> line.contains("ERROR:") || line.startsWith("psql:"))
          .reduce((first, second) -> second)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("Comando do teste PostgreSQL 17 falhou: " + detail);
    }
    return exit;
  }

  private static Path requiredFile(String name) {
    Path path = Path.of(required(name)).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Arquivo obrigatorio ausente: " + name);
    }
    return path;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variavel obrigatoria ausente: " + name);
    }
    return value;
  }

  private static int indexOfPrefix(List<String> values, String prefix) {
    for (int index = 0; index < values.size(); index++) {
      if (values.get(index).startsWith(prefix)) return index;
    }
    throw new IllegalArgumentException("Variavel de snapshot ausente");
  }

  private static List<String> withoutSetting(List<String> values, String prefix) {
    List<String> result = new ArrayList<>(values);
    int valueIndex = indexOfPrefix(result, prefix);
    result.remove(valueIndex);
    result.remove(valueIndex - 1);
    return result;
  }

  private static List<String> replacingSetting(
      List<String> values, String prefix, String replacement) {
    List<String> result = new ArrayList<>(values);
    result.set(indexOfPrefix(result, prefix), replacement);
    return result;
  }

  private static String setting(List<String> values, String prefix) {
    return values.get(indexOfPrefix(values, prefix)).substring(prefix.length());
  }

  private static String sqlLiteral(String value) {
    return "'" + value.replace("'", "''") + "'";
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
