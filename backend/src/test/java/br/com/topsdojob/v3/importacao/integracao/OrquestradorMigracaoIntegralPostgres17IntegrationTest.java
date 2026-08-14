package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.anuncio.ManifestoMidiaAnuncios;
import br.com.topsdojob.v3.importacao.anuncio.SnapshotAnunciosFaseUm;
import br.com.topsdojob.v3.importacao.comercial.SnapshotConfiguracaoComercialFaseTres;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois;
import br.com.topsdojob.v3.importacao.financeiro.SnapshotFinanceiroFaseQuatro;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_INTEGRAL_PG17_ENABLED",
    matches = "true")
class OrquestradorMigracaoIntegralPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final OffsetDateTime CAPTURADO_EM =
      OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private static final UUID ATOR = UUID.fromString("80000000-0000-4000-8000-000000000001");

  @Test
  void executaDryRunRetomaMesmaExecucaoERejeitaDestinoDeOutroPacote() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-integracao-" + sufixo + "-net";
    String container = "topsv3-integracao-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao_integracao";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-migracao-integracao-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", rede);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", rede, "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuario,
        "-e", CREDENTIAL_ENV + "=" + credencialEfemera,
        "postgres:17-alpine");
    try {
      aguardarPostgres(container, usuario, banco, logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
      int porta = portaPublicada(container, logs);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + banco,
          usuario,
          credencialEfemera);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      DataSourceTransactionManager transacoes = new DataSourceTransactionManager(dataSource);
      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      ExecutorFake executor = new ExecutorFake();
      OrquestradorMigracaoIntegral orquestrador = new OrquestradorMigracaoIntegral(
          dataSource, transacoes, mapper, executor);
      PacoteMigracaoIntegral pacote = pacote("pacote-integral-a", "v1");

      var dryRun = orquestrador.executar(pacote, ModoMigracaoIntegral.DRY_RUN, 10);
      assertThat(dryRun.status()).isEqualTo("CONCLUIDA");
      assertThat(dryRun.fasesConcluidas()).containsExactlyElementsOf(
          OrquestradorMigracaoIntegral.ordemCanonica());
      assertThat(contar(jdbc, "importacao_execucao")).isZero();

      executor.limpar();
      executor.interromperEm(FaseMigracaoIntegral.FINANCEIRO);
      assertThatThrownBy(() -> orquestrador.executar(pacote, ModoMigracaoIntegral.APPLY, 10))
          .isInstanceOf(OrquestradorMigracaoIntegral.ExecucaoInterrompidaException.class);
      assertThat(jdbc.queryForObject(
          "SELECT status FROM importacao_execucao", String.class)).isEqualTo("EM_EXECUCAO");
      assertThat(executor.chamadas()).contains(FaseMigracaoIntegral.MANIFESTO_MIDIA)
          .doesNotContain(FaseMigracaoIntegral.FINGERPRINT);

      executor.limpar();
      executor.interromperEm(null);
      var retomada = orquestrador.executar(pacote, ModoMigracaoIntegral.APPLY, 10);
      assertThat(retomada.retomada()).isTrue();
      assertThat(retomada.status()).isEqualTo("CONCLUIDA");
      assertThat(executor.chamadas()).startsWith(FaseMigracaoIntegral.FINANCEIRO)
          .endsWith(FaseMigracaoIntegral.FINGERPRINT);
      assertThat(jdbc.queryForObject(
          "SELECT status FROM importacao_execucao", String.class)).isEqualTo("CONCLUIDA");

      executor.limpar();
      var idempotente = orquestrador.executar(pacote, ModoMigracaoIntegral.APPLY, 10);
      assertThat(idempotente.retomada()).isTrue();
      assertThat(executor.chamadas()).isEmpty();
      assertThat(contar(jdbc, "importacao_execucao")).isEqualTo(1);

      assertThatThrownBy(() -> orquestrador.executar(
          pacote("pacote-integral-a", "v2"), ModoMigracaoIntegral.APPLY, 10))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("pacote diverge");
      assertThatThrownBy(() -> orquestrador.executar(
          pacote("pacote-integral-b", "v1"), ModoMigracaoIntegral.APPLY, 10))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("destino nao esta vazio");

      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
    } finally {
      command(false, logs.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      command(false, logs.resolve("cleanup-network.log"),
          "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static PacoteMigracaoIntegral pacote(String pacoteId, String versao) {
    var base = new SnapshotBaseMigracaoIntegral.Snapshot(
        "base", CAPTURADO_EM, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of());
    var faseUm = new SnapshotAnunciosFaseUm.Snapshot(
        "fase-1", CAPTURADO_EM, ATOR, Map.of(), Map.of(), Map.of(), List.of(),
        new ManifestoMidiaAnuncios(List.of()), List.of());
    var faseDois = new SnapshotConteudoSeoFaseDois.Snapshot(
        "fase-2", CAPTURADO_EM, ATOR, Map.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of());
    var faseTres = new SnapshotConfiguracaoComercialFaseTres.Snapshot(
        "fase-3", CAPTURADO_EM, ATOR, List.of(), List.of(), List.of(), List.of());
    var faseQuatro = new SnapshotFinanceiroFaseQuatro.Snapshot(
        "fase-4", CAPTURADO_EM, ATOR, Map.of(), Map.of(), Map.of(), List.of(),
        List.of(), List.of());
    return new PacoteMigracaoIntegral(
        pacoteId,
        versao,
        base,
        faseUm,
        faseDois,
        faseTres,
        faseQuatro,
        new ManifestoMidiaFaseCinco(List.of()));
  }

  private static long contar(JdbcTemplate jdbc, String tabela) {
    Long total = jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
    return total == null ? 0 : total;
  }

  private static void aguardarPostgres(
      String container, String usuario, String banco, Path logs) throws Exception {
    for (int tentativa = 0; tentativa < 60; tentativa++) {
      int exit = command(false, logs.resolve("pg-ready.log"),
          "docker", "exec", container, "pg_isready", "--host", "127.0.0.1",
          "--username", usuario, "--dbname", banco);
      if (exit == 0) {
        return;
      }
      Thread.sleep(500L);
    }
    throw new IllegalStateException("PostgreSQL 17 descartavel nao ficou pronto");
  }

  private static void flyway(
      String rede,
      String container,
      String usuario,
      String banco,
      String credencial,
      String acao,
      Path logs) throws Exception {
    command(true, logs.resolve("flyway-" + acao + ".log"),
        "docker", "run", "--pull=never", "--rm", "--network", rede,
        "-v", MIGRATIONS + ":/flyway/sql:ro", "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + container + ":5432/" + banco,
        "-user=" + usuario, FLYWAY_CREDENTIAL_OPTION + credencial,
        "-locations=filesystem:/flyway/sql", acao);
  }

  private static int portaPublicada(String container, Path logs) throws Exception {
    Path log = logs.resolve("docker-port.log");
    command(true, log, "docker", "port", container, "5432/tcp");
    Matcher matcher = Pattern.compile(".*:(\\d+)$").matcher(Files.readString(log).trim());
    if (!matcher.find()) {
      throw new IllegalStateException("porta do PostgreSQL descartavel nao encontrada");
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int command(boolean check, Path output, String... argumentos) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(argumentos);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (check && exit != 0) {
      String detalhe = Files.readString(output).lines()
          .filter(linha -> linha.contains("ERROR:") || linha.contains("Exception"))
          .reduce((primeiro, segundo) -> segundo)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("comando descartavel falhou: " + detalhe);
    }
    return exit;
  }

  private static void deleteTree(Path raiz) throws IOException {
    if (!Files.exists(raiz)) {
      return;
    }
    try (var caminhos = Files.walk(raiz)) {
      for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(caminho);
      }
    }
  }

  private static final class ExecutorFake implements ExecutorFasesMigracaoIntegral {
    private final List<FaseMigracaoIntegral> chamadas = new ArrayList<>();
    private FaseMigracaoIntegral interrupcao;

    @Override
    public ResultadoEtapa executar(FaseMigracaoIntegral fase, Contexto contexto) {
      chamadas.add(fase);
      if (fase == interrupcao) {
        throw new OrquestradorMigracaoIntegral.ExecucaoInterrompidaException();
      }
      return new ResultadoEtapa(1, 0, 0, 0, Map.of("fase", fase.name()));
    }

    List<FaseMigracaoIntegral> chamadas() {
      return List.copyOf(chamadas);
    }

    void limpar() {
      chamadas.clear();
    }

    void interromperEm(FaseMigracaoIntegral fase) {
      interrupcao = fase;
    }
  }
}
