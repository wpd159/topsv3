package br.com.topsdojob.v3.importacao.anuncio;

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
    named = "IMPORTADOR_ANUNCIOS_FASE1_PG17_ENABLED",
    matches = "true")
class ImportadorAnunciosFaseUmPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CONTAINER_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";

  @Test
  void importaSnapshotSinteticoComRetomadaEIdempotencia() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-anuncios-fase1-" + sufixo + "-net";
    String container = "topsv3-anuncios-fase1-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-anuncios-fase1-");

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
      semearDependencias(jdbc);
      ImportadorAnunciosFaseUm importador = new ImportadorAnunciosFaseUm(
          dataSource,
          new DataSourceTransactionManager(dataSource),
          new ObjectMapper().findAndRegisterModules());
      var snapshot = AnunciosFaseUmFixture.snapshotCompleto();

      ResultadoImportacaoAnuncios interrompido = importador.executar(snapshot, 4);
      assertThat(interrompido.status()).isEqualTo("EM_EXECUCAO");
      assertThat(interrompido.processadosNestaChamada()).isEqualTo(4);
      assertThat(interrompido.restantes()).isEqualTo(8);

      ResultadoImportacaoAnuncios retomado = importador.executar(snapshot, 100);
      assertThat(retomado.status()).isEqualTo("CONCLUIDA_COM_PENDENCIAS");
      assertThat(retomado.analisados()).isEqualTo(12);
      assertThat(retomado.importados()).isEqualTo(9);
      assertThat(retomado.quarentena()).isEqualTo(3);
      assertThat(retomado.descartados()).isZero();
      assertThat(retomado.importados() + retomado.quarentena() + retomado.descartados())
          .isEqualTo(retomado.analisados());
      assertThat(retomado.publicos()).isEqualTo(1);
      assertThat(retomado.emRevisao()).isEqualTo(3);
      assertThat(retomado.revisoesImportadas()).isEqualTo(2);
      assertThat(retomado.filhosImportados()).isEqualTo(4);
      assertThat(retomado.processadosNestaChamada()).isEqualTo(8);
      assertThat(retomado.restantes()).isZero();

      validarEstados(jdbc);
      validarVirtualModeracaoClassificacao(jdbc);
      validarRevisoesFilhosEQuarentena(jdbc);
      validarForaDoEscopoVazio(jdbc);
      validarStagingSanitizado(jdbc);
      validarIdsDeterministicos(jdbc);

      ResultadoImportacaoAnuncios segundaExecucao = importador.executar(snapshot, 100);
      assertThat(segundaExecucao.novosAnunciosNestaChamada()).isZero();
      assertThat(segundaExecucao.novasRevisoesNestaChamada()).isZero();
      assertThat(segundaExecucao.novosFilhosNestaChamada()).isZero();
      assertThat(segundaExecucao.processadosNestaChamada()).isZero();
      assertThat(segundaExecucao.importados()).isEqualTo(9);
      assertThat(segundaExecucao.revisoesImportadas()).isEqualTo(2);
      assertThat(segundaExecucao.filhosImportados()).isEqualTo(4);
      assertThat(contar(jdbc, "anuncio")).isEqualTo(9);
      assertThat(contar(jdbc, "revisao_anuncio")).isEqualTo(2);
      assertThat(contar(jdbc, "anuncio_midia_revisao")).isEqualTo(1);
      assertThat(contar(jdbc, "importacao_execucao")).isEqualTo(1);
    } finally {
      command(false, logs.resolve("cleanup-container.log"),
          "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"),
          "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static void validarEstados(JdbcTemplate jdbc) {
    assertStatus(jdbc, "pub", "PUBLICADO", "APROVADO");
    assertStatus(jdbc, "sem-midia", "PENDENTE_REVISAO", "PENDENTE");
    assertStatus(jdbc, "pausado", "PAUSADO", "APROVADO");
    assertStatus(jdbc, "rejeitado", "REJEITADO", "REJEITADO");
    assertStatus(jdbc, "removido", "REMOVIDO", "APROVADO");
    assertStatus(jdbc, "bloqueado", "BLOQUEADO", "APROVADO");
    assertStatus(jdbc, "revisao", "PENDENTE_REVISAO", "PENDENTE");
    assertStatus(jdbc, "virtual-nulo", "PENDENTE_REVISAO", "PENDENTE");
    assertStatus(jdbc, "owner-inativo", "PAUSADO", "APROVADO");
    assertThat(statusBusca(jdbc, "pub")).isEqualTo("PUBLICAVEL");
    assertThat(statusBusca(jdbc, "pausado")).isEqualTo("NAO_PUBLICAVEL");
    assertThat(statusBusca(jdbc, "rejeitado")).isEqualTo("NOINDEX");
    assertThat(statusBusca(jdbc, "removido")).isEqualTo("REMOVIDO");
  }

  private static void validarVirtualModeracaoClassificacao(JdbcTemplate jdbc) {
    assertThat(virtual(jdbc, "pub")).isTrue();
    assertThat(virtual(jdbc, "sem-midia")).isFalse();
    assertThat(virtual(jdbc, "virtual-nulo")).isFalse();
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio_servicos WHERE anuncio_id = ? AND servico = 'VIDEOCHAMADA'",
        Long.class, anuncioId("pub"))).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio_servicos WHERE anuncio_id = ? AND servico = 'VIDEOCHAMADA'",
        Long.class, anuncioId("sem-midia"))).isZero();
    assertThat(jdbc.queryForObject(
        "SELECT visibilidade_midia FROM anuncio_midia WHERE anuncio_id = ?",
        String.class, anuncioId("pub"))).isEqualTo("RESTRITA_18");
    assertThat(jdbc.queryForObject(
        "SELECT status || '|' || visibilidade_midia FROM anuncio_midia WHERE id = ?",
        String.class, uuid("anuncio-midia", "video-pendente")))
        .isEqualTo("PENDENTE|RESTRITA_18");

    assertThat(jdbc.queryForObject(
        "SELECT payload_solicitado ->> 'classificacaoConteudo' FROM revisao_anuncio WHERE id = ?",
        String.class, uuid("revisao-anuncio", "rev-publicada-001")))
        .isEqualTo("ADULT_RESTRICTED");
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM decisao_moderacao WHERE ator_usuario_id = ?",
        Long.class, AnunciosFaseUmFixture.ATOR_MAPEADO_ID)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM decisao_moderacao WHERE ator_usuario_id IS NULL",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM anuncio_bloqueio_juridico WHERE bloqueado_por_id = ?",
        Long.class, AnunciosFaseUmFixture.ATOR_SISTEMA_ID)).isEqualTo(1);
  }

  private static void validarRevisoesFilhosEQuarentena(JdbcTemplate jdbc) {
    assertThat(contar(jdbc, "revisao_anuncio")).isEqualTo(2);
    assertThat(contar(jdbc, "decisao_moderacao")).isEqualTo(2);
    assertThat(contar(jdbc, "anuncio_midia_revisao")).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_mapeamento "
            + "WHERE entidade_tipo = 'REVISAO_FILHO' AND status = 'MAPEADO'",
        Long.class)).isEqualTo(4);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia WHERE codigo = 'REVISAO_FILHO_ORFAO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia "
            + "WHERE codigo = 'REVISAO_FILHO_DUPLICADO_DESCARTADO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_pendencia "
            + "WHERE codigo = 'ANUNCIO_ATENDIMENTO_VIRTUAL_INDETERMINADO'",
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM importacao_mapeamento "
            + "WHERE entidade_tipo = 'ANUNCIO' AND status = 'DIVERGENTE'",
        Long.class)).isEqualTo(3);
  }

  private static void validarForaDoEscopoVazio(JdbcTemplate jdbc) {
    for (String tabela : List.of(
        "ativacao_beneficio", "pagamento", "saldo_credito_usuario", "story_anuncio",
        "evento_visualizacao", "clique_whatsapp")) {
      assertThat(contar(jdbc, tabela)).as(tabela).isZero();
    }
  }

  private static void validarStagingSanitizado(JdbcTemplate jdbc) {
    String payloads = jdbc.queryForObject(
        "SELECT string_agg(payload_normalizado_json::text, ' ') FROM stg_anuncio",
        String.class);
    assertThat(payloads)
        .doesNotContain("email", "telefone", "whatsapp", "cpf", "nomeCivil",
            "CONTEUDO_SINTETICO", "MIGRACAO_QA_ANUNCIO");
  }

  private static void validarIdsDeterministicos(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT id FROM anuncio WHERE slug = 'migracao-qa-pub'", UUID.class))
        .isEqualTo(anuncioId("pub"));
    assertThat(jdbc.queryForObject(
        "SELECT id FROM revisao_anuncio WHERE id = ?", UUID.class,
        uuid("revisao-anuncio", "rev-publicada-001")))
        .isEqualTo(uuid("revisao-anuncio", "rev-publicada-001"));
  }

  private static void assertStatus(
      JdbcTemplate jdbc, String origem, String status, String moderacao) {
    String valor = jdbc.queryForObject(
        "SELECT status || '|' || status_moderacao FROM anuncio WHERE id = ?",
        String.class, anuncioId(origem));
    assertThat(valor).isEqualTo(status + "|" + moderacao);
  }

  private static String statusBusca(JdbcTemplate jdbc, String origem) {
    return jdbc.queryForObject(
        "SELECT status_publicacao FROM documento_busca_anuncio WHERE anuncio_id = ?",
        String.class, anuncioId(origem));
  }

  private static boolean virtual(JdbcTemplate jdbc, String origem) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT atendimento_exclusivamente_virtual FROM anuncio WHERE id = ?",
        Boolean.class, anuncioId(origem)));
  }

  private static long contar(JdbcTemplate jdbc, String tabela) {
    if (!tabela.matches("^[a-z_]+$")) {
      throw new IllegalArgumentException("Tabela invalida");
    }
    return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
  }

  private static void semearDependencias(JdbcTemplate jdbc) {
    OffsetDateTime agora = AnunciosFaseUmFixture.AGORA;
    jdbc.update(
        "INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em) VALUES (?, 'QQ', ?, ?, ?)",
        AnunciosFaseUmFixture.ESTADO_ID, "Estado QA", "estado qa", agora);
    jdbc.update(
        "INSERT INTO cidade (id, estado_id, nome, nome_normalizado, slug, criado_em) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        AnunciosFaseUmFixture.CIDADE_ID, AnunciosFaseUmFixture.ESTADO_ID,
        "Cidade QA", "cidade qa", "cidade-qa", agora);
    jdbc.update(
        "INSERT INTO bairro (id, cidade_id, nome, nome_normalizado, slug, criado_em) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        AnunciosFaseUmFixture.BAIRRO_ID, AnunciosFaseUmFixture.CIDADE_ID,
        "Bairro QA", "bairro qa", "bairro-qa", agora);
    inserirUsuario(jdbc, AnunciosFaseUmFixture.PROPRIETARIO_ATIVO_ID,
        "Conta sintetica ativa", "ATIVO", "ANUNCIANTE", agora);
    inserirUsuario(jdbc, AnunciosFaseUmFixture.PROPRIETARIO_INATIVO_ID,
        "Conta sintetica inativa", "DESATIVADO", "ANUNCIANTE", agora);
    inserirUsuario(jdbc, AnunciosFaseUmFixture.ATOR_MAPEADO_ID,
        "Ator sintetico", "ATIVO", "STAFF", agora);
    inserirUsuario(jdbc, AnunciosFaseUmFixture.ATOR_SISTEMA_ID,
        "Sistema sintetico", "ATIVO", "SISTEMA", agora);
  }

  private static void inserirUsuario(
      JdbcTemplate jdbc,
      UUID id,
      String nome,
      String status,
      String tipo,
      OffsetDateTime agora) {
    jdbc.update(
        "INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id, nome, status, tipo, agora, agora);
  }

  private static UUID anuncioId(String origem) {
    return uuid("anuncio", origem);
  }

  private static UUID uuid(String... partes) {
    return UUID.nameUUIDFromBytes(
        String.join(":", partes).getBytes(StandardCharsets.UTF_8));
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
