package br.com.topsdojob.v3.importacao.comercial;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(
    named = "IMPORTADOR_CONFIGURACAO_COMERCIAL_FASE3_PG17_ENABLED",
    matches = "true")
class ImportadorConfiguracaoComercialFaseTresPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CONTAINER_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";

  @Test
  void importaSomenteConfiguracaoComercialComRetomadaEIdempotencia() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-comercial-" + sufixo + "-net";
    String container = "topsv3-comercial-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao_comercial";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-comercial-");

    command(true, logs.resolve("network.log"), "docker", "network", "create", rede);
    command(true, logs.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "--network", rede, "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuario,
        "-e", CONTAINER_CREDENTIAL_ENV + "=" + credencialEfemera,
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
      semearAtor(jdbc);
      ImportadorConfiguracaoComercialFaseTres importador = importador(dataSource);
      var snapshot = ConfiguracaoComercialFaseTresFixture.snapshotCompleto();

      ResultadoImportacaoConfiguracaoComercial interrompido =
          importador.executar(snapshot, 7);
      assertThat(interrompido.status()).isEqualTo("EM_EXECUCAO");
      assertThat(interrompido.processadosNestaChamada()).isEqualTo(7);
      assertThat(interrompido.restantes()).isEqualTo(21);

      ResultadoImportacaoConfiguracaoComercial retomado =
          importador.executar(snapshot, 100);
      assertThat(retomado.status()).isEqualTo("CONCLUIDA_COM_PENDENCIAS");
      assertThat(retomado.produtosAnalisados()).isEqualTo(9);
      assertThat(retomado.produtosImportadosAtivos()).isEqualTo(2);
      assertThat(retomado.produtosImportadosInativos()).isEqualTo(1);
      assertThat(retomado.produtosHistoricos()).isEqualTo(1);
      assertThat(retomado.produtosDescartados()).isZero();
      assertThat(retomado.produtosQuarentena()).isEqualTo(5);
      assertThat(retomado.opcoesAnalisadas()).isEqualTo(10);
      assertThat(retomado.opcoesImportadas()).isEqualTo(3);
      assertThat(retomado.opcoesQuarentena()).isEqualTo(7);
      assertThat(retomado.pacotesAnalisados()).isEqualTo(7);
      assertThat(retomado.pacotesImportadosAtivos()).isEqualTo(1);
      assertThat(retomado.pacotesImportadosInativos()).isEqualTo(1);
      assertThat(retomado.pacotesDescartados()).isZero();
      assertThat(retomado.pacotesQuarentena()).isEqualTo(5);
      assertThat(retomado.storiesAnalisadas()).isEqualTo(2);
      assertThat(retomado.storiesImportadas()).isEqualTo(1);
      assertThat(retomado.storiesQuarentena()).isEqualTo(1);
      assertThat(retomado.processadosNestaChamada()).isEqualTo(21);
      assertThat(retomado.novosRegistrosNestaChamada()).isEqualTo(3);
      assertThat(retomado.restantes()).isZero();

      validarMigrations(jdbc);
      validarCatalogo(jdbc);
      validarPacotes(jdbc);
      validarStories(jdbc);
      validarQuarentenaEReconciliacao(jdbc, retomado);
      validarIsolamento(jdbc);

      String identidadeAntes = identidadeImportacao(jdbc, retomado.execucaoId());
      flyway(rede, container, usuario, banco, credencialEfemera, "migrate", logs);
      flyway(rede, container, usuario, banco, credencialEfemera, "validate", logs);
      ResultadoImportacaoConfiguracaoComercial segundaExecucao =
          importador.executar(snapshot, 100);
      assertThat(segundaExecucao.processadosNestaChamada()).isZero();
      assertThat(segundaExecucao.novosRegistrosNestaChamada()).isZero();
      assertThat(segundaExecucao.restantes()).isZero();
      assertThat(identidadeImportacao(jdbc, retomado.execucaoId()))
          .isEqualTo(identidadeAntes);
      assertThat(contar(jdbc, "importacao_execucao")).isEqualTo(1);
      assertThat(contar(jdbc, "importacao_mapeamento")).isEqualTo(28);
      assertThat(contar(jdbc, "beneficio_premium")).isEqualTo(7);
      assertThat(contar(jdbc, "beneficio_premium_opcao")).isEqualTo(24);
      assertThat(contar(jdbc, "plano_credito")).isEqualTo(4);
      assertThat(contar(jdbc, "story_configuracao_comercial")).isEqualTo(1);

      ResultadoImportacaoConfiguracaoComercial storyIncompativel =
          importador.executar(
              ConfiguracaoComercialFaseTresFixture.snapshotStoryIncompativel(),
              10);
      assertThat(storyIncompativel.storiesImportadas()).isZero();
      assertThat(storyIncompativel.storiesQuarentena()).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT custo_creditos FROM story_configuracao_comercial WHERE id = 1",
          Integer.class)).isEqualTo(6);
      validarIsolamento(jdbc);
    } finally {
      command(false, logs.resolve("cleanup-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"), "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static ImportadorConfiguracaoComercialFaseTres importador(
      DriverManagerDataSource dataSource) {
    return new ImportadorConfiguracaoComercialFaseTres(
        dataSource,
        new DataSourceTransactionManager(dataSource),
        new ObjectMapper().findAndRegisterModules(),
        ConfiguracaoComercialFaseTresFixture.matriz(),
        new SanitizadorDescricaoComercialLegada());
  }

  private static void validarMigrations(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM flyway_schema_history WHERE success = true",
        Long.class)).isEqualTo(51);
  }

  private static void validarCatalogo(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForMap(
        """
        SELECT nome, descricao, escopo, afeta_ranking, ativo, ordem_exibicao
        FROM beneficio_premium WHERE codigo = 'ANUNCIO_TOPO'
        """))
        .containsEntry("nome", "Anuncio no topo")
        .containsEntry("escopo", "ANUNCIO")
        .containsEntry("afeta_ranking", true)
        .containsEntry("ativo", true)
        .containsEntry("ordem_exibicao", 11)
        .doesNotContainValue("Nome legado nao autoritativo");

    assertThat(jdbc.queryForObject(
        "SELECT ativo FROM beneficio_premium WHERE codigo = 'WHATSAPP_CARD'",
        Boolean.class)).isFalse();
    assertThat(jdbc.queryForObject(
        """
        SELECT opcao.ativo
        FROM beneficio_premium_opcao opcao
        JOIN beneficio_premium beneficio ON beneficio.id = opcao.beneficio_id
        WHERE beneficio.codigo = 'WHATSAPP_CARD'
          AND opcao.duracao_dias = 7
          AND opcao.versao_regra = 1
        """,
        Boolean.class)).isFalse();
    assertThat(jdbc.queryForObject(
        """
        SELECT custo_creditos
        FROM beneficio_premium_opcao opcao
        JOIN beneficio_premium beneficio ON beneficio.id = opcao.beneficio_id
        WHERE beneficio.codigo = 'OCULTAR_IDADE'
          AND opcao.duracao_dias = 14
          AND opcao.versao_regra = 1
        """,
        Integer.class)).isEqualTo(18);
    assertThat(jdbc.queryForObject(
        """
        SELECT preco_referencia
        FROM beneficio_premium_opcao opcao
        JOIN beneficio_premium beneficio ON beneficio.id = opcao.beneficio_id
        WHERE beneficio.codigo = 'OCULTAR_IDADE'
          AND opcao.duracao_dias = 14
          AND opcao.versao_regra = 1
        """,
        java.math.BigDecimal.class)).isEqualByComparingTo("12.50");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM beneficio_premium
        WHERE codigo IN ('PRODUTO_NAO_CANONICO', 'DESTAQUE_CONTA_HISTORICO')
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM beneficio_premium WHERE codigo = 'ANUNCIO_TOPO'",
        Long.class)).isEqualTo(1);
  }

  private static void validarPacotes(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForMap(
        """
        SELECT quantidade_creditos, valor, moeda, ativo, ordem_exibicao, descricao
        FROM plano_credito WHERE codigo = 'PACOTE_50'
        """))
        .containsEntry("quantidade_creditos", 55)
        .containsEntry("moeda", "BRL")
        .containsEntry("ativo", true)
        .containsEntry("ordem_exibicao", 12);
    assertThat(jdbc.queryForObject(
        "SELECT valor FROM plano_credito WHERE codigo = 'PACOTE_50'",
        java.math.BigDecimal.class)).isEqualByComparingTo("9.99");
    assertThat(jdbc.queryForObject(
        "SELECT descricao FROM plano_credito WHERE codigo = 'PACOTE_50'",
        String.class))
        .isEqualTo("Pacote inicial")
        .doesNotContainIgnoringCase("script", "nao executar");
    assertThat(jdbc.queryForMap(
        """
        SELECT quantidade_creditos, valor, ativo
        FROM plano_credito WHERE codigo = 'PACOTE_150'
        """))
        .containsEntry("quantidade_creditos", 150)
        .containsEntry("ativo", false);
    assertThat(jdbc.queryForObject(
        "SELECT valor FROM plano_credito WHERE codigo = 'PACOTE_150'",
        java.math.BigDecimal.class)).isEqualByComparingTo("0.00");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM plano_credito
        WHERE codigo IN ('PACOTE_400', 'PACOTE_900', 'PACOTE_901')
        """,
        Long.class)).isZero();

    String staging = jdbc.queryForObject(
        """
        SELECT payload_normalizado_json::text
        FROM stg_credito
        WHERE tabela_origem = 'planos_credito'
          AND id_origem = 'package-01-prata'
        """,
        String.class);
    assertThat(staging)
        .contains("\"quantidadeCreditosBase\": 50")
        .contains("\"bonusCreditos\": 5")
        .contains("\"quantidadeCreditosTotal\": 55");
  }

  private static void validarStories(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForMap(
        """
        SELECT ativo, custo_creditos, atualizado_por
        FROM story_configuracao_comercial WHERE id = 1
        """))
        .containsEntry("ativo", true)
        .containsEntry("custo_creditos", 6)
        .containsEntry("atualizado_por", ConfiguracaoComercialFaseTresFixture.ATOR_SISTEMA_ID);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM beneficio_premium WHERE codigo = 'STORIES'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*)
        FROM beneficio_premium_opcao opcao
        JOIN beneficio_premium beneficio ON beneficio.id = opcao.beneficio_id
        WHERE beneficio.codigo = 'STORIES'
        """,
        Long.class)).isZero();
  }

  private static void validarQuarentenaEReconciliacao(
      JdbcTemplate jdbc,
      ResultadoImportacaoConfiguracaoComercial resultado) {
    assertThat(
        resultado.produtosImportadosAtivos()
            + resultado.produtosImportadosInativos()
            + resultado.produtosHistoricos()
            + resultado.produtosDescartados()
            + resultado.produtosQuarentena())
        .isEqualTo(resultado.produtosAnalisados());
    assertThat(
        resultado.pacotesImportadosAtivos()
            + resultado.pacotesImportadosInativos()
            + resultado.pacotesDescartados()
            + resultado.pacotesQuarentena())
        .isEqualTo(resultado.pacotesAnalisados());
    assertThat(resultado.storiesImportadas() + resultado.storiesQuarentena())
        .isEqualTo(resultado.storiesAnalisadas());

    for (String codigo : List.of(
        "CONFIG_COMERCIAL_CODIGO_DESCONHECIDO",
        "CONFIG_COMERCIAL_CODIGO_DUPLICADO",
        "CONFIG_COMERCIAL_PRODUTO_SEM_OPCAO_VALIDA",
        "CONFIG_COMERCIAL_DURACAO_INCOMPATIVEL",
        "CONFIG_COMERCIAL_VALOR_INVALIDO",
        "CONFIG_COMERCIAL_CREDITOS_INVALIDOS",
        "CONFIG_COMERCIAL_ORDEM_INVALIDA",
        "CONFIG_COMERCIAL_BENEFICIO_ORFAO",
        "PACOTE_COMERCIAL_DUPLICADO",
        "PACOTE_COMERCIAL_PRECO_INVALIDO",
        "PACOTE_COMERCIAL_CODIGO_DESCONHECIDO",
        "PACOTE_COMERCIAL_VALIDADE_NAO_SUPORTADA",
        "PACOTE_COMERCIAL_QUANTIDADE_INVALIDA",
        "STORY_CONFIGURACAO_INCOMPATIVEL")) {
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM importacao_pendencia WHERE codigo = ?",
          Long.class,
          codigo)).as(codigo).isPositive();
    }
  }

  private static void validarIsolamento(JdbcTemplate jdbc) {
    for (String tabela : List.of(
        "grupo_ativacao_beneficio",
        "ativacao_beneficio",
        "pagamento",
        "pagamento_evento",
        "pagamento_webhook",
        "pagamento_conciliacao",
        "saldo_credito_usuario",
        "movimento_credito",
        "arquivo_midia",
        "anuncio_midia",
        "story_anuncio")) {
      assertThat(contar(jdbc, tabela)).as(tabela).isZero();
    }
    String staging = jdbc.queryForObject(
        """
        SELECT coalesce(string_agg(payload_normalizado_json::text, ' '), '')
        FROM (
          SELECT payload_normalizado_json FROM stg_premium
          UNION ALL
          SELECT payload_normalizado_json FROM stg_credito
        ) dados
        """,
        String.class);
    assertThat(staging.toLowerCase())
        .doesNotContain(
            "password", "secret", "token", "pix", "object_key",
            "private", "http://", "https://", "@");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_mapeamento
        WHERE hash_origem IS NULL OR length(hash_origem) <> 64
        """,
        Long.class)).isZero();
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

  private static void semearAtor(JdbcTemplate jdbc) {
    OffsetDateTime agora = ConfiguracaoComercialFaseTresFixture.AGORA;
    jdbc.update(
        """
        INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em)
        VALUES (?, 'Sistema sintetico comercial', 'ATIVO', 'SISTEMA', ?, ?)
        """,
        ConfiguracaoComercialFaseTresFixture.ATOR_SISTEMA_ID,
        agora,
        agora);
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
