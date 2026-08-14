package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.CredencialLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.DocumentoKycLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.LocalidadeLegada;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.OrfaoLegado;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoLocalidade;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.TipoOrfao;
import br.com.topsdojob.v3.importacao.integracao.SnapshotBaseMigracaoIntegral.UsuarioLegado;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
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
import java.util.List;
import java.util.Set;
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
class ImportadorBaseMigracaoIntegralPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";
  private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private static final UUID EXECUCAO = UUID.fromString("70000000-0000-4000-8000-000000000001");
  private static final UUID ATOR = UUID.fromString("70000000-0000-4000-8000-000000000002");

  @Test
  void importaBaseConsolidaKycClassificaOrfaosEIsolaColisao() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-base-integral-" + sufixo + "-net";
    String container = "topsv3-base-integral-" + sufixo + "-pg17";
    String usuarioBanco = "migracaoqa";
    String banco = "topsv3_migracao_base";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-migracao-base-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", rede);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", rede, "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuarioBanco,
        "-e", CREDENTIAL_ENV + "=" + credencialEfemera,
        "postgres:17-alpine");
    try {
      aguardarPostgres(container, usuarioBanco, banco, logs);
      flyway(rede, container, usuarioBanco, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuarioBanco, banco, credencialEfemera, "validate", logs);
      int porta = portaPublicada(container, logs);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + banco,
          usuarioBanco,
          credencialEfemera);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      jdbc.update(
          "INSERT INTO importacao_execucao (id, sistema_origem, status, iniciado_em, criado_em) "
              + "VALUES (?, 'TOPSDOJOB_MIGRACAO_INTEGRAL', 'EM_EXECUCAO', ?, ?)",
          EXECUCAO, AGORA, AGORA);
      ImportadorBaseMigracaoIntegral importador = new ImportadorBaseMigracaoIntegral(
          dataSource,
          new DataSourceTransactionManager(dataSource),
          new ObjectMapper().findAndRegisterModules());
      var snapshot = snapshot();
      var consolidacao = new PoliticaKycMigracaoIntegral().consolidar(
          snapshot.documentosKyc(), manifesto());

      importarTodos(() -> importador.importarLocalidades(snapshot, EXECUCAO, 2));
      importarTodos(() -> importador.importarUsuarios(snapshot, EXECUCAO, 2));
      importarTodos(() -> importador.importarCredenciais(snapshot, EXECUCAO, 2));
      importador.registrarPlanejamentoKycEOrfaos(snapshot, EXECUCAO, consolidacao);
      importarTodos(() -> importador.importarKyc(snapshot, EXECUCAO, consolidacao, 2, ATOR));

      assertThat(contar(jdbc, "estado")).isEqualTo(1);
      assertThat(contar(jdbc, "cidade")).isEqualTo(1);
      assertThat(contar(jdbc, "bairro")).isEqualTo(1);
      assertThat(contar(jdbc, "usuario")).isEqualTo(4);
      assertThat(contar(jdbc, "credencial_usuario")).isEqualTo(1);
      assertThat(contar(jdbc, "documento_usuario")).isEqualTo(1);
      assertThat(jdbc.queryForList(
          "SELECT status FROM documento_usuario ORDER BY status", String.class))
          .containsExactly("VALIDADO");
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'KYC_OWNERSHIP_CRUZADO'",
          Long.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia "
              + "WHERE codigo = 'KYC_USUARIO_AFETADO_VINCULO_CRUZADO'",
          Long.class)).isEqualTo(3);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'CARTEIRA_ORFA_QUARENTENA'",
          Long.class)).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia "
              + "WHERE codigo IN ('HISTORICO_CREDITO_ORFAO_QUARENTENA', "
              + "'PAGAMENTO_ORFAO_QUARENTENA')",
          Long.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'SUPORTE_ORFAO'",
          Long.class)).isZero();
      assertThat(contar(jdbc, "movimento_credito")).isZero();
      assertThat(contar(jdbc, "pagamento")).isZero();
      assertThat(importador.reconciliar(snapshot, EXECUCAO).aprovada()).isTrue();

      importarTodos(() -> importador.importarLocalidades(snapshot, EXECUCAO, 10));
      importarTodos(() -> importador.importarUsuarios(snapshot, EXECUCAO, 10));
      importarTodos(() -> importador.importarCredenciais(snapshot, EXECUCAO, 10));
      importarTodos(() -> importador.importarKyc(snapshot, EXECUCAO, consolidacao, 10, ATOR));
      assertThat(contar(jdbc, "usuario")).isEqualTo(4);
      assertThat(contar(jdbc, "documento_usuario")).isEqualTo(1);

      var conflito = new SnapshotBaseMigracaoIntegral.Snapshot(
          "base-conflito",
          AGORA,
          List.of(),
          List.of(usuario("usuario-conflito", UUID.randomUUID(), "qa-a@example.invalid", "ATIVO")),
          List.of(), List.of(), List.of(), List.of(), List.of());
      var resultadoConflito = importador.importarUsuarios(conflito, EXECUCAO, 10);
      assertThat(resultadoConflito.quarentenas()).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT status FROM stg_usuario WHERE id_origem = 'usuario-conflito'",
          String.class)).isEqualTo("PENDENTE_REVISAO");
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia "
              + "WHERE codigo = 'USUARIO_UNICIDADE_DIVERGENTE'",
          Long.class)).isEqualTo(1);
      assertThat(contar(jdbc, "usuario")).isEqualTo(4);
    } finally {
      command(false, logs.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      command(false, logs.resolve("cleanup-network.log"),
          "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static SnapshotBaseMigracaoIntegral.Snapshot snapshot() {
    List<UsuarioLegado> usuarios = List.of(
        usuario("ator", ATOR, "qa-ator@example.invalid", "ATIVO"),
        usuario("usuario-a", UUID.fromString("70000000-0000-4000-8000-000000000003"),
            "qa-a@example.invalid", "ATIVO"),
        usuario("usuario-b", UUID.fromString("70000000-0000-4000-8000-000000000004"),
            "qa-b@example.invalid", "ATIVO"),
        usuario("usuario-c", UUID.fromString("70000000-0000-4000-8000-000000000005"),
            "qa-c@example.invalid", "ATIVO"));
    return new SnapshotBaseMigracaoIntegral.Snapshot(
        "base-integral-sintetica",
        AGORA,
        List.of(
            new LocalidadeLegada(
                "estado-go", TipoLocalidade.ESTADO, null, null, "GO", "Goias",
                "goias", "goias", AGORA.minusYears(1)),
            new LocalidadeLegada(
                "cidade-qa", TipoLocalidade.CIDADE, "estado-go", "local-qa", null,
                "Cidade QA", "cidade qa", "cidade-qa", AGORA.minusYears(1)),
            new LocalidadeLegada(
                "bairro-qa", TipoLocalidade.BAIRRO, "cidade-qa", "bairro-qa", null,
                "Bairro QA", "bairro qa", "bairro-qa", AGORA.minusYears(1))),
        usuarios,
        List.of(new CredencialLegada(
            "credencial-a", "usuario-a", "$2a$12$abcdefghijklmnopqrstuu012345678901234567890123456789",
            "BCRYPT", AGORA.minusDays(2), true, AGORA.minusDays(3))),
        List.of(
            documento("doc-a-validado", "envio-a-validado", "usuario-a", "midia-a-validada",
                "VALIDADO", AGORA.minusDays(10)),
            documento("doc-a-pendente", "envio-a-pendente", "usuario-a", "midia-a-pendente",
                "PENDENTE", AGORA.minusDays(1)),
            documento("doc-b-validado", "envio-b-validado", "usuario-b", "midia-b-validada",
                "VALIDADO", AGORA.minusDays(2)),
            documento("doc-c-validado", "envio-c-validado", "usuario-c", "midia-c-validada",
                "VALIDADO", AGORA.minusDays(2)),
            documento("doc-cruzado-a", "envio-cruzado-a", "usuario-a", "midia-cruzada",
                "VALIDADO", AGORA.minusDays(4)),
            documento("doc-cruzado-b", "envio-cruzado-b", "usuario-b", "midia-cruzada",
                "VALIDADO", AGORA.minusDays(3))),
        List.of(
            new OrfaoLegado(TipoOrfao.CARTEIRA, "carteira-orfa", 1, 2_100),
            new OrfaoLegado(TipoOrfao.HISTORICO_CREDITO, "historicos-orfaos", 3, 0),
            new OrfaoLegado(TipoOrfao.PAGAMENTO, "pagamentos-orfaos", 16, 0),
            new OrfaoLegado(TipoOrfao.SUPORTE, "suporte-orfaos", 4, 0)),
        List.of(),
        List.of());
  }

  private static UsuarioLegado usuario(String origem, UUID id, String email, String status) {
    boolean ator = origem.equals("ator");
    return new UsuarioLegado(
        origem, id, ator ? "Sistema QA" : "Anunciante QA", email, null, status,
        ator ? "SISTEMA" : "ANUNCIANTE", null, null, null, AGORA.minusDays(5), null,
        AGORA.minusDays(10), AGORA.minusDays(1), null,
        ator ? Set.of("ADMIN") : Set.of("USUARIO"));
  }

  private static DocumentoKycLegado documento(
      String id,
      String envio,
      String usuario,
      String item,
      String status,
      OffsetDateTime atualizadoEm) {
    return new DocumentoKycLegado(
        id, envio, usuario, item, "IDENTIDADE", "UNICO", status,
        atualizadoEm.minusHours(1), atualizadoEm, null, null, null);
  }

  private static ManifestoMidiaFaseCinco manifesto() {
    return new ManifestoMidiaFaseCinco(List.of(
        item("midia-a-validada", "usuario-a"),
        item("midia-a-pendente", "usuario-a"),
        item("midia-b-validada", "usuario-b"),
        item("midia-c-validada", "usuario-c"),
        item("midia-cruzada", "usuario-a")));
  }

  private static Item item(String id, String proprietario) {
    return new Item(
        id, EntidadeTipo.KYC, id, null, proprietario, null, id,
        Finalidade.KYC_IDENTIDADE, TipoMidia.DOCUMENTO, Visibilidade.PRIVADA,
        EstadoModeracao.NAO_APLICAVEL, true, false, 0, "image/jpeg", 8,
        "b".repeat(64),
        new Origem(TipoOrigem.OBJECT_STORAGE, StorageArea.PRIVATE_DOCUMENT, "origem/" + id),
        new Destino(StorageArea.PRIVATE_DOCUMENT, "documentos-sinteticos", "destino/" + id),
        Decisao.IMPORTAR, null);
  }

  private static void importarTodos(java.util.function.Supplier<ImportadorBaseMigracaoIntegral.ResultadoLote> lote) {
    ImportadorBaseMigracaoIntegral.ResultadoLote resultado;
    do {
      resultado = lote.get();
    } while (resultado.restantes() > 0);
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
      String rede, String container, String usuario, String banco, String credencial,
      String acao, Path logs) throws Exception {
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
}
