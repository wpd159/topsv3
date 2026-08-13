package br.com.topsdojob.v3.importacao.financeiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.admin.pagamentos.RelatorioReceitaFiltro;
import br.com.topsdojob.v3.persistence.repository.admin.AdminRelatorioReceitaJdbcRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
    named = "IMPORTADOR_FINANCEIRO_FASE4_PG17_ENABLED",
    matches = "true")
class ImportadorFinanceiroFaseQuatroPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";

  @Test
  void importaHistoricoAtivacoesESaldoSemRepetirEfeitosFinanceiros() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-financeiro-" + sufixo + "-net";
    String container = "topsv3-financeiro-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao_financeiro";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-financeiro-");

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
      semearDependencias(jdbc);
      validarBancoVazioAntesDoEnsaio(jdbc);
      long beneficiosAntes = contar(jdbc, "beneficio_premium");

      ImportadorFinanceiroFaseQuatro importador = new ImportadorFinanceiroFaseQuatro(
          dataSource,
          new DataSourceTransactionManager(dataSource),
          new ObjectMapper().findAndRegisterModules(),
          new MatrizPagamentoHistoricoImportacao());
      var snapshot = FinanceiroFaseQuatroFixture.snapshotCompleto();

      ResultadoImportacaoFinanceiraFaseQuatro interrompido =
          importador.executar(snapshot, 6);
      assertThat(interrompido.status()).isEqualTo("EM_EXECUCAO");
      assertThat(interrompido.processadosNestaChamada()).isEqualTo(6);
      assertThat(interrompido.restantes()).isEqualTo(16);

      ResultadoImportacaoFinanceiraFaseQuatro retomado =
          importador.executar(snapshot, 100);
      assertThat(retomado.status()).isEqualTo("CONCLUIDA_COM_PENDENCIAS");
      assertThat(retomado.pagamentosAnalisados()).isEqualTo(10);
      assertThat(retomado.pagamentosImportados()).isEqualTo(5);
      assertThat(retomado.pagamentosAprovadosImportados()).isEqualTo(2);
      assertThat(retomado.pagamentosQuarentena()).isEqualTo(5);
      assertThat(retomado.pagamentosDescartados()).isZero();
      assertThat(retomado.eventosHistoricos()).isEqualTo(5);
      assertThat(retomado.gruposAnalisados()).isEqualTo(7);
      assertThat(retomado.gruposImportados()).isEqualTo(3);
      assertThat(retomado.gruposQuarentena()).isEqualTo(2);
      assertThat(retomado.gruposDescartados()).isEqualTo(2);
      assertThat(retomado.ativacoesAnalisadas()).isEqualTo(9);
      assertThat(retomado.ativacoesImportadas()).isEqualTo(4);
      assertThat(retomado.ativacoesQuarentena()).isEqualTo(3);
      assertThat(retomado.ativacoesDescartadas()).isEqualTo(2);
      assertThat(retomado.carteirasAnalisadas()).isEqualTo(5);
      assertThat(retomado.carteirasComSaldoImportadas()).isEqualTo(2);
      assertThat(retomado.carteirasSaldoZero()).isEqualTo(1);
      assertThat(retomado.carteirasQuarentena()).isEqualTo(2);
      assertThat(retomado.movimentosSaldoInicial()).isEqualTo(2);
      assertThat(retomado.saldoPositivoAnalisado()).isEqualTo(3600);
      assertThat(retomado.saldoInicialImportado()).isEqualTo(1500);
      assertThat(retomado.saldoPositivoQuarentena()).isEqualTo(2100);
      assertThat(retomado.restantes()).isZero();

      validarMigrations(jdbc);
      validarPagamentos(jdbc, dataSource);
      validarAtivacoes(jdbc);
      validarSaldo(jdbc);
      validarQuarentenas(jdbc);
      validarIsolamento(jdbc, beneficiosAntes);
      validarConstraintSaldoInicial(jdbc);

      String identidade = identidadeImportacao(jdbc, retomado.execucaoId());
      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
      ResultadoImportacaoFinanceiraFaseQuatro segunda = importador.executar(snapshot, 100);
      assertThat(segunda.processadosNestaChamada()).isZero();
      assertThat(segunda.pagamentosNovosNestaChamada()).isZero();
      assertThat(segunda.eventosNovosNestaChamada()).isZero();
      assertThat(segunda.gruposNovosNestaChamada()).isZero();
      assertThat(segunda.ativacoesNovasNestaChamada()).isZero();
      assertThat(segunda.movimentosNovosNestaChamada()).isZero();
      assertThat(segunda.restantes()).isZero();
      assertThat(identidadeImportacao(jdbc, retomado.execucaoId())).isEqualTo(identidade);

      ResultadoImportacaoFinanceiraFaseQuatro terceira = importador.executar(snapshot, 100);
      assertThat(terceira.processadosNestaChamada()).isZero();
      assertThat(terceira.pagamentosNovosNestaChamada()).isZero();
      assertThat(terceira.eventosNovosNestaChamada()).isZero();
      assertThat(terceira.gruposNovosNestaChamada()).isZero();
      assertThat(terceira.ativacoesNovasNestaChamada()).isZero();
      assertThat(terceira.movimentosNovosNestaChamada()).isZero();
      assertThat(identidadeImportacao(jdbc, retomado.execucaoId())).isEqualTo(identidade);
      validarPagamentos(jdbc, dataSource);
      validarAtivacoes(jdbc);
      validarSaldo(jdbc);
    } finally {
      command(false, logs.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      command(false, logs.resolve("cleanup-network.log"),
          "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static void validarBancoVazioAntesDoEnsaio(JdbcTemplate jdbc) {
    for (String tabela : List.of(
        "pagamento",
        "pagamento_evento",
        "pagamento_conciliacao",
        "pagamento_webhook",
        "grupo_ativacao_beneficio",
        "ativacao_beneficio",
        "movimento_credito",
        "saldo_credito_usuario")) {
      assertThat(contar(jdbc, tabela)).as(tabela).isZero();
    }
  }

  private static void validarMigrations(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM flyway_schema_history WHERE success = true",
        Long.class)).isEqualTo(51);
  }

  private static void validarPagamentos(
      JdbcTemplate jdbc,
      DriverManagerDataSource dataSource) {
    assertThat(contar(jdbc, "pagamento")).isEqualTo(5);
    assertThat(contar(jdbc, "pagamento_evento")).isEqualTo(5);
    assertThat(contar(jdbc, "pagamento_conciliacao")).isZero();
    assertThat(contar(jdbc, "pagamento_webhook")).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM pagamento WHERE txid IS NOT NULL OR ambiente IS NOT NULL",
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM pagamento
        WHERE status_interno IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'ERRO')
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM pagamento WHERE status_interno = 'APROVADO'",
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT coalesce(sum(valor), 0) FROM pagamento WHERE status_interno = 'APROVADO'",
        java.math.BigDecimal.class)).isEqualByComparingTo("30.00");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM movimento_credito
        WHERE origem = 'PAGAMENTO' OR referencia_tipo = 'PAGAMENTO'
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM pagamento
        WHERE provedor = 'MERCADO_PAGO_LEGADO'
          AND metodo = 'LEGADO'
          AND ambiente IS NULL
        """,
        Long.class)).isEqualTo(2);

    AdminRelatorioReceitaJdbcRepository relatorio =
        new AdminRelatorioReceitaJdbcRepository(new NamedParameterJdbcTemplate(dataSource));
    RelatorioReceitaFiltro filtro = new RelatorioReceitaFiltro(
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 2),
        OffsetDateTime.parse("2026-07-01T03:00:00Z"),
        OffsetDateTime.parse("2026-08-03T03:00:00Z"),
        RelatorioReceitaFiltro.Status.TODOS,
        RelatorioReceitaFiltro.Metodo.TODOS,
        null,
        null);
    assertThat(relatorio.metricas(filtro).receitaConfirmada()).isEqualByComparingTo("30.00");
    assertThat(relatorio.metricas(filtro).pagamentosConfirmados()).isEqualTo(2);
    assertThat(relatorio.transacoes(filtro, 0, 20, "MAIS_RECENTES").total()).isEqualTo(5);
    assertThat(relatorio.conciliacao(filtro))
        .extracting("semConciliacao", "divergentes", "semMovimento")
        .containsExactly(0L, 0L, 0L);
  }

  private static void validarAtivacoes(JdbcTemplate jdbc) {
    assertThat(contar(jdbc, "grupo_ativacao_beneficio")).isEqualTo(3);
    assertThat(contar(jdbc, "ativacao_beneficio")).isEqualTo(4);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM grupo_ativacao_beneficio WHERE status <> 'ATIVO'",
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM ativacao_beneficio WHERE status <> 'ATIVA'",
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*)
        FROM ativacao_beneficio a
        JOIN grupo_ativacao_beneficio g ON g.id = a.grupo_ativacao_id
        WHERE a.inicio_em <> g.validade_inicio_em OR a.fim_em <> g.validade_fim_em
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM grupo_ativacao_beneficio WHERE origem = 'CREDITO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM grupo_ativacao_beneficio WHERE origem = 'IMPORTACAO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM grupo_ativacao_beneficio WHERE origem = 'ADMIN'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM grupo_ativacao_beneficio g
        JOIN ativacao_beneficio a ON a.grupo_ativacao_id = g.id
        WHERE g.origem = 'CREDITO'
        """,
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM ativacao_beneficio WHERE opcao_id IS NOT NULL",
        Long.class)).isZero();
  }

  private static void validarSaldo(JdbcTemplate jdbc) {
    assertThat(contar(jdbc, "movimento_credito")).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM movimento_credito
        WHERE tipo = 'MIGRACAO_SALDO_INICIAL'
          AND direcao = 'CREDITO'
          AND origem = 'IMPORTACAO'
          AND referencia_tipo = 'SNAPSHOT_IMPORTACAO'
          AND saldo_antes = 0
        """,
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT coalesce(sum(quantidade), 0) FROM movimento_credito",
        Long.class)).isEqualTo(1500);
    assertThat(contar(jdbc, "saldo_credito_usuario")).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT status FROM usuario WHERE id = ?",
        String.class,
        FinanceiroFaseQuatroFixture.USUARIO_BLOQUEADO_ID)).isEqualTo("SUSPENSO");
    assertThat(jdbc.queryForObject(
        """
        SELECT saldo_depois FROM movimento_credito
        WHERE usuario_id = ? AND tipo = 'MIGRACAO_SALDO_INICIAL'
        """,
        Integer.class,
        FinanceiroFaseQuatroFixture.USUARIO_BLOQUEADO_ID)).isEqualTo(500);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM pagamento p
        JOIN movimento_credito m ON m.referencia_id = p.id
        WHERE m.tipo = 'MIGRACAO_SALDO_INICIAL'
        """,
        Long.class)).isZero();
  }

  private static void validarQuarentenas(JdbcTemplate jdbc) {
    for (String codigo : List.of(
        "PAGAMENTO_HISTORICO_ESTADO_INCOMPLETO",
        "PAGAMENTO_HISTORICO_VALOR_INCONSISTENTE",
        "PAGAMENTO_HISTORICO_CREDITOS_INCONSISTENTES",
        "PAGAMENTO_HISTORICO_USUARIO_NAO_MAPEADO",
        "PAGAMENTO_HISTORICO_PLANO_NAO_MAPEADO",
        "ATIVACAO_FINANCEIRA_GRUPO_INCONSISTENTE",
        "ATIVACAO_FINANCEIRA_OWNERSHIP_DIVERGENTE",
        "SALDO_FINANCEIRO_USUARIO_NAO_MAPEADO",
        "SALDO_FINANCEIRO_NEGATIVO")) {
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia WHERE codigo = ?",
          Long.class,
          codigo)).as(codigo).isPositive();
    }
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
        WHERE tabela_origem = 'carteiras_saldo_final'
          AND status = 'DIVERGENTE'
        """,
        Long.class)).isEqualTo(2);
  }

  private static void validarIsolamento(JdbcTemplate jdbc, long beneficiosAntes) throws IOException {
    assertThat(contar(jdbc, "anuncio")).isEqualTo(2);
    assertThat(contar(jdbc, "beneficio_premium")).isEqualTo(beneficiosAntes);
    for (String tabela : List.of(
        "story_anuncio",
        "arquivo_midia",
        "anuncio_midia")) {
      assertThat(contar(jdbc, tabela)).as(tabela).isZero();
    }
    String staging = jdbc.queryForObject(
        """
        SELECT coalesce(string_agg(payload_normalizado_json::text, ' '), '')
        FROM (
          SELECT payload_normalizado_json FROM stg_pagamento
          UNION ALL
          SELECT payload_normalizado_json FROM stg_premium
          UNION ALL
          SELECT payload_normalizado_json FROM stg_credito
        ) dados
        """,
        String.class);
    assertThat(staging.toLowerCase())
        .doesNotContain(
            "password", "secret", "token", "txid", "endtoendid", "pix",
            "object_key", "private", "http://", "https://", "@", "cpf", "email");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
        WHERE hash_origem IS NULL OR length(hash_origem) <> 64
        """,
        Long.class)).isZero();
    String importador = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3",
        "importacao", "financeiro", "ImportadorFinanceiroFaseQuatro.java"));
    assertThat(importador)
        .doesNotContain("RestClient", "WebClient", "HttpClient", "RestTemplate")
        .doesNotContain("EfiPagamentoGateway", "MercadoPagoGateway");
  }

  private static void validarConstraintSaldoInicial(JdbcTemplate jdbc) {
    assertThatThrownBy(() -> jdbc.update(
        """
        INSERT INTO movimento_credito (
          id, usuario_id, tipo, direcao, quantidade, saldo_antes, saldo_depois,
          origem, referencia_tipo, referencia_id, idempotency_key, ator_usuario_id,
          observacao, criado_em, metadata_json
        ) VALUES (
          'f8300000-0000-4000-8000-000000000499', ?, 'MIGRACAO_SALDO_INICIAL',
          'CREDITO', 1, 0, 1, 'IMPORTACAO', 'SNAPSHOT_IMPORTACAO',
          'f8400000-0000-4000-8000-000000000499', 'saldo-duplicado-sintetico', ?,
          'Tentativa sintetica duplicada', ?,
          '{"snapshotId":"s","snapshotFingerprint":"f","usuarioLegadoHash":"h",'
            || '"saldoImportado":1,"divergenciaHistorica":false,"versaoImportador":"v"}'::jsonb
        )
        """,
        FinanceiroFaseQuatroFixture.USUARIO_ATIVO_ID,
        FinanceiroFaseQuatroFixture.ATOR_SISTEMA_ID,
        FinanceiroFaseQuatroFixture.AGORA))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(contar(jdbc, "movimento_credito")).isEqualTo(2);
  }

  private static String identidadeImportacao(JdbcTemplate jdbc, UUID execucaoId) {
    return jdbc.queryForObject(
        """
        SELECT string_agg(
          id::text || ':' || hash_origem || ':' || coalesce(entidade_v3_id::text, '-'),
          '|' ORDER BY tabela_origem, id_origem)
        FROM importacao_mapeamento
        WHERE execucao_id = ?
        """,
        String.class,
        execucaoId);
  }

  private static long contar(JdbcTemplate jdbc, String tabela) {
    if (!tabela.matches("^[a-z_]+$")) {
      throw new IllegalArgumentException("Tabela invalida");
    }
    return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
  }

  private static void semearDependencias(JdbcTemplate jdbc) {
    OffsetDateTime agora = FinanceiroFaseQuatroFixture.AGORA;
    jdbc.update(
        """
        INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
        VALUES
          (?, 'Ator sintetico', 'ATIVO', 'SISTEMA', ?, ?, 0),
          (?, 'Conta sintetica ativa', 'ATIVO', 'ANUNCIANTE', ?, ?, 0),
          (?, 'Conta sintetica bloqueada', 'SUSPENSO', 'ANUNCIANTE', ?, ?, 0),
          (?, 'Conta sintetica zero', 'ATIVO', 'ANUNCIANTE', ?, ?, 0),
          (?, 'Conta sintetica ownership', 'ATIVO', 'ANUNCIANTE', ?, ?, 0)
        """,
        FinanceiroFaseQuatroFixture.ATOR_SISTEMA_ID, agora, agora,
        FinanceiroFaseQuatroFixture.USUARIO_ATIVO_ID, agora, agora,
        FinanceiroFaseQuatroFixture.USUARIO_BLOQUEADO_ID, agora, agora,
        FinanceiroFaseQuatroFixture.USUARIO_ZERO_ID, agora, agora,
        FinanceiroFaseQuatroFixture.USUARIO_OUTRO_ID, agora, agora);
    jdbc.update(
        """
        INSERT INTO anuncio (
          id, usuario_id, slug, titulo, status, status_moderacao,
          categoria, criado_em, atualizado_em, versao
        ) VALUES
          (?, ?, 'anuncio-sintetico-ativo', 'Anuncio sintetico', 'PUBLICADO',
            'APROVADO', 'ACOMPANHANTE_FEMININA', ?, ?, 0),
          (?, ?, 'anuncio-sintetico-outro', 'Outro anuncio sintetico', 'PUBLICADO',
            'APROVADO', 'ACOMPANHANTE_FEMININA', ?, ?, 0)
        """,
        FinanceiroFaseQuatroFixture.ANUNCIO_ATIVO_ID,
        FinanceiroFaseQuatroFixture.USUARIO_ATIVO_ID,
        agora,
        agora,
        FinanceiroFaseQuatroFixture.ANUNCIO_OUTRO_ID,
        FinanceiroFaseQuatroFixture.USUARIO_OUTRO_ID,
        agora,
        agora);
    jdbc.update(
        """
        INSERT INTO plano_credito (
          id, codigo, nome, quantidade_creditos, valor, moeda, ativo,
          criado_em, atualizado_em, descricao, ordem_exibicao
        ) VALUES (?, 'PACOTE_SYNTHETIC', 'Pacote sintetico', 100, 10.00, 'BRL', true,
          ?, ?, 'Somente fixture', 1)
        """,
        FinanceiroFaseQuatroFixture.PLANO_ID,
        agora,
        agora);
    String[] codigos = {
        "ANUNCIO_TOPO", "WHATSAPP_CARD", "OCULTAR_IDADE",
        "VIDEO_1", "CARROSSEL_FOTOS", "FOTOS_EXTRA_5"
    };
    for (int indice = 0; indice < codigos.length; indice++) {
      jdbc.update(
          """
          INSERT INTO beneficio_premium (
            id, codigo, nome, descricao, escopo, afeta_ranking, ativo,
            criado_em, ordem_exibicao, atualizado_em
          ) VALUES (?, ?, ?, 'Somente fixture', 'ANUNCIO', false, true, ?, ?, ?)
          ON CONFLICT (codigo) DO NOTHING
          """,
          UUID.nameUUIDFromBytes(("beneficio-sintetico-" + codigos[indice]).getBytes()),
          codigos[indice],
          "Beneficio sintetico " + indice,
          agora,
          indice,
          agora);
    }
  }

  private static void aguardarPostgres(
      String container,
      String usuario,
      String banco,
      Path logs) throws Exception {
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
    String saida = Files.readString(log).trim();
    Matcher matcher = Pattern.compile(".*:(\\d+)$").matcher(saida);
    if (!matcher.find()) {
      throw new IllegalStateException("Porta do PostgreSQL descartavel nao encontrada");
    }
    return Integer.parseInt(matcher.group(1));
  }

  private static int command(boolean check, Path output, String... argumentos)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(argumentos);
    builder.redirectErrorStream(true);
    builder.redirectOutput(output.toFile());
    int exit = builder.start().waitFor();
    if (check && exit != 0) {
      String detalhe = Files.readString(output).lines()
          .filter(linha -> linha.contains("ERROR:") || linha.contains("Exception"))
          .reduce((primeiro, segundo) -> segundo)
          .orElse("erro sem detalhe sanitizado");
      throw new IllegalStateException("Comando descartavel falhou: " + detalhe);
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
