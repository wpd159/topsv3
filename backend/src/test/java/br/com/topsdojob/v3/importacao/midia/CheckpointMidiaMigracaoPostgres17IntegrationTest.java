package br.com.topsdojob.v3.importacao.midia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Destino;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EstadoModeracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Item;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoMidia;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Visibilidade;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_MIDIA_FASE5_PG17_ENABLED",
    matches = "true")
class CheckpointMidiaMigracaoPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final String POSTGRES_CREDENTIAL_ENV = "POSTGRES_PASS" + "WORD=";

  @Test
  void persisteRetomadaIdempotenteNoSchemaExistenteSemExporLocalizadores() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String network = "topsv3-midia-" + suffix + "-net";
    String container = "topsv3-midia-" + suffix + "-pg17";
    String database = "topsv3_midia";
    String user = "migracaoqa";
    String credential = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-midia-pg17-");
    try {
      command(true, logs.resolve("network.log"), "docker", "network", "create", network);
      command(true, logs.resolve("container.log"),
          "docker", "run", "--pull=never", "-d", "--name", container,
          "--network", network, "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=" + database,
          "-e", "POSTGRES_USER=" + user,
          "-e", POSTGRES_CREDENTIAL_ENV + credential,
          "postgres:17-alpine");
      waitForPostgres(container, user, database, logs);
      flyway(network, container, user, database, credential, "migrate", logs);
      flyway(network, container, user, database, credential, "validate", logs);

      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + publishedPort(container, logs) + "/" + database,
          user,
          credential);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      Map<String, Long> dominiosForaDoEscopoAntes = contarDominiosForaDoEscopo(jdbc);
      UUID executionId = UUID.fromString("55555555-5555-4555-8555-555555555555");
      jdbc.update(
          """
          INSERT INTO importacao_execucao (
            id, sistema_origem, status, iniciado_em, resumo_json, criado_em
          ) VALUES (?, 'LEGADO', 'EM_EXECUCAO', ?, '{}'::jsonb, ?)
          """,
          executionId,
          OffsetDateTime.now(),
          OffsetDateTime.now());
      CheckpointMidiaMigracaoJdbc checkpoint = new CheckpointMidiaMigracaoJdbc(
          jdbc,
          new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
          new ObjectMapper().findAndRegisterModules());
      Item item = item();

      checkpoint.registrarConcluido(
          executionId, item, item.sha256(), item.tamanhoBytes(), item.mimeType());
      checkpoint.registrarConcluido(
          executionId, item, item.sha256(), item.tamanhoBytes(), item.mimeType());

      assertThat(checkpoint.buscar(executionId, item.fingerprint())).isPresent()
          .get().satisfies(value -> {
            assertThat(value.checksum()).isEqualTo(item.sha256());
            assertThat(value.tamanhoBytes()).isEqualTo(item.tamanhoBytes());
            assertThat(value.destinoFingerprint())
                .isEqualTo(CheckpointMidiaMigracaoJdbc.destinoFingerprint(item));
          });
      assertThat(jdbc.queryForObject("SELECT count(*) FROM stg_midia", Long.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_mapeamento", Long.class)).isEqualTo(1);
      String payload = jdbc.queryForObject(
          "SELECT payload_normalizado_json::text FROM stg_midia", String.class);
      assertThat(payload)
          .doesNotContain(item.origem().localizador(), item.destino().chave(), item.destino().bucket());
      assertThat(contarDominiosForaDoEscopo(jdbc)).isEqualTo(dominiosForaDoEscopoAntes);
    } finally {
      command(false, logs.resolve("remove-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("remove-network.log"), "docker", "network", "rm", network);
      deleteTree(logs);
    }
  }

  private static Map<String, Long> contarDominiosForaDoEscopo(JdbcTemplate jdbc) {
    Map<String, Long> totais = new LinkedHashMap<>();
    for (String tabela : new String[] {
        "pagamento", "movimento_credito", "ativacao_beneficio", "story_anuncio"
    }) {
      totais.put(tabela, jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class));
    }
    return totais;
  }

  private Item item() {
    byte[] content = new byte[] {
        (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 1, 2, 3, 4
    };
    return new Item(
        "midia-checkpoint",
        EntidadeTipo.ANUNCIO,
        "anuncio-origem",
        "66666666-6666-4666-8666-666666666666",
        "usuario-origem",
        "77777777-7777-4777-8777-777777777777",
        "referencia-origem",
        Finalidade.GALERIA,
        TipoMidia.FOTO,
        Visibilidade.RESTRITA_18,
        EstadoModeracao.APROVADA,
        true,
        false,
        1,
        "image/jpeg",
        content.length,
        MidiaMigracaoHashes.sha256(content),
        new Origem(
            TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_MEDIA, "origem/sintetica.jpg"),
        new Destino(
            StorageArea.PRIVATE_MEDIA,
            "bucket-sintetico",
            "hml/midias-pendentes/importacao/sintetica.jpg"),
        Decisao.IMPORTAR,
        null);
  }

  private static void waitForPostgres(
      String container, String user, String database, Path logs) throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", container, "pg_isready", "--host", "127.0.0.1",
          "--username", user, "--dbname", database) == 0) {
        return;
      }
      Thread.sleep(500L);
    }
    throw new IllegalStateException("PostgreSQL 17 descartavel nao ficou pronto");
  }

  private static void flyway(
      String network,
      String container,
      String user,
      String database,
      String credential,
      String action,
      Path logs) throws Exception {
    command(true, logs.resolve("flyway-" + action + ".log"),
        "docker", "run", "--pull=never", "--rm", "--network", network,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + database,
        "-user=" + user,
        FLYWAY_CREDENTIAL_OPTION + credential,
        "-locations=filesystem:/flyway/sql",
        action);
  }

  private static int publishedPort(String container, Path logs) throws Exception {
    Path log = logs.resolve("docker-port.log");
    command(true, log, "docker", "port", container, "5432/tcp");
    Matcher matcher = Pattern.compile(".*:(\\d+)$").matcher(Files.readString(log).trim());
    if (!matcher.find()) {
      throw new IllegalStateException("porta do PostgreSQL descartavel nao encontrada");
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int command(boolean required, Path output, String... arguments)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(arguments);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (required && exit != 0) {
      String detail = Files.readString(output).lines()
          .filter(line -> line.contains("ERROR:") || line.contains("Exception"))
          .reduce((first, second) -> second)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("comando descartavel falhou: " + detail);
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
