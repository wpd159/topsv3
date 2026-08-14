package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_INTEGRAL_PRODUTOR_PG17_ENABLED",
    matches = "true")
class ProdutorPacoteMigracaoIntegralPostgres17IntegrationTest {

  private static final String SNAPSHOT = "a".repeat(64);
  private static final OffsetDateTime CAPTURADO_EM =
      OffsetDateTime.parse("2026-08-01T12:00:00Z");
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";

  @Test
  void produzPacoteCanonicoAtomicoSemCredenciaisERejeitaSobrescritaDivergente()
      throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String container = "topsv3-produtor-integral-" + sufixo + "-pg17";
    String usuarioBanco = "migracaoqa";
    String banco = "topsv3_legado_sintetico";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path temporario = Files.createTempDirectory("topsv3-produtor-integral-");

    command(true, temporario.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuarioBanco,
        "-e", CREDENTIAL_ENV + "=" + credencialEfemera,
        "postgres:17-alpine");
    try {
      aguardarPostgres(container, usuarioBanco, banco, temporario);
      int porta = portaPublicada(container, temporario);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + banco,
          usuarioBanco,
          credencialEfemera);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      criarSchemaLegado(jdbc);
      inserirFotografia(jdbc);

      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      Path manifestos = Files.createDirectory(temporario.resolve("manifestos"));
      Path manifesto = manifestos.resolve("fase-5.json");
      mapper.writeValue(
          manifesto.toFile(),
          ArquivoManifestoMidiaFaseCinco.criar(
              "TOPSDOJOB_LEGADO",
              SNAPSHOT,
              "ensaio-manifesto",
              CAPTURADO_EM,
              "f".repeat(64),
              manifesto(),
              mapper));
      Path saida = temporario.resolve("pacote-integral.json");
      var parametros = new ProdutorPacoteMigracaoIntegral.Parametros(
          "ensaio-produtor",
          "TOPSDOJOB_LEGADO",
          SNAPSHOT,
          CAPTURADO_EM,
          manifestos,
          manifesto,
          saida);
      ProdutorPacoteMigracaoIntegral produtor =
          new ProdutorPacoteMigracaoIntegral(
              dataSource,
              mapper,
              new DataSourceTransactionManager(dataSource),
              destinoProperties());

      var criado = produtor.produzir(parametros);
      var preservado = produtor.produzir(parametros);
      ArquivoPacoteMigracaoIntegral arquivo =
          new RepositorioPacoteMigracaoIntegral(mapper).carregar(saida);

      assertThat(criado.estado())
          .isEqualTo(RepositorioPacoteMigracaoIntegral.EstadoEscrita.CRIADO);
      assertThat(preservado.estado())
          .isEqualTo(RepositorioPacoteMigracaoIntegral.EstadoEscrita.PRESERVADO);
      assertThat(preservado.arquivoSha256()).isEqualTo(criado.arquivoSha256());
      assertThat(arquivo.pacote().base().credenciais()).isEmpty();
      assertThat(arquivo.pacote().base().usuarios()).hasSize(1);
      assertThat(arquivo.pacote().base().localidades())
          .extracting(SnapshotBaseMigracaoIntegral.LocalidadeLegada::idOrigem)
          .containsExactly("estado:1", "cidade:1", "bairro:1");
      assertThat(arquivo.pacote().base().usuariosStaging()).hasSize(2);
      assertThat(criado.usuariosStaging()).containsEntry("TOTAL", 2L);
      assertThat(criado.usuariosStaging()).containsEntry("CORRESPONDENCIA_CANONICA", 1L);
      assertThat(criado.usuariosStaging()).containsEntry("STAGING_ONLY", 1L);

      jdbc.update(
          "INSERT INTO usuarios_staging "
              + "(id, username, email, nome_completo, cpf, telefone, data_nascimento, status, "
              + "role, criado_em, verificado, is_verificado) "
              + "VALUES (3, 'staging-extra', null, 'QA Extra', null, null, "
              + "null, 'ATIVO', 'USER', ?, false, false)",
          CAPTURADO_EM.minusDays(1));

      assertThatThrownBy(() -> produtor.produzir(parametros))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("nao sera sobrescrito");
    } finally {
      command(false, temporario.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      deleteTree(temporario);
    }
  }

  private static void criarSchemaLegado(JdbcTemplate jdbc) {
    List<String> comandos = List.of(
        "CREATE TABLE usuarios (id bigint PRIMARY KEY, username text, email text, "
            + "nome_completo text, cpf text, telefone text, data_nascimento date, status text, "
            + "role text, criado_em timestamptz, verificado boolean, is_verificado boolean)",
        "CREATE TABLE usuarios_staging (LIKE usuarios INCLUDING ALL)",
        "CREATE TABLE estado (id bigint PRIMARY KEY, uf text, nome text, "
            + "nome_normalizado text, slug text)",
        "CREATE TABLE cidade (id bigint PRIMARY KEY, estado_id bigint, nome text, "
            + "nome_normalizado text, slug text)",
        "CREATE TABLE bairro (id bigint PRIMARY KEY, cidade_id bigint, nome text, "
            + "nome_normalizado text, slug text)",
        "CREATE TABLE anuncios (id bigint PRIMARY KEY, usuario_id bigint, slug text, titulo text, "
            + "descricao text, status text, categoria text, preco numeric, cidade_id bigint, "
            + "bairro_id bigint, content_classification text, criado_em timestamptz, "
            + "removido_logicamente_em timestamptz, "
            + "removido_logicamente_motivo text, visualizacoes bigint)",
        "CREATE TABLE anuncio_servicos (anuncio_id bigint, servico text)",
        "CREATE TABLE anuncio_local_atendimento (anuncio_id bigint, local_atendimento text)",
        "CREATE TABLE faq (id bigint PRIMARY KEY, pergunta text, resposta text, categoria text)",
        "CREATE TABLE avisos (id bigint PRIMARY KEY, titulo text, descricao text, "
            + "local_exibicao text, frequencia_exibicao text, status text, "
            + "permite_dispensar boolean, ativo_de timestamptz, ativo_ate timestamptz, "
            + "criado_por_id bigint, criado_em timestamptz, atualizado_em timestamptz)",
        "CREATE TABLE blog_categorias (id bigint PRIMARY KEY, nome text, slug text, "
            + "sort_order int, ativo boolean, created_at timestamptz, updated_at timestamptz)",
        "CREATE TABLE blog_posts (id bigint PRIMARY KEY, blog_categoria_id bigint, titulo text, "
            + "slug text, resumo text, conteudo text, autor_nome text, status text, "
            + "seo_title text, seo_description text, created_by_user_id bigint, "
            + "created_at timestamptz, updated_at timestamptz, published_at timestamptz)",
        "CREATE TABLE site_content_entries (id bigint PRIMARY KEY, content_key text, "
            + "titulo text, corpo text, updated_by bigint, created_at timestamptz, "
            + "updated_at timestamptz)",
        "CREATE TABLE feature_catalogo (id bigint PRIMARY KEY, codigo text, nome text, "
            + "descricao text, escopo text, ativo boolean, custo_creditos int, "
            + "duracao_horas int)",
        "CREATE TABLE feature_catalogo_duracoes (id bigint PRIMARY KEY, "
            + "feature_catalogo_id bigint, dias int, custo_creditos int, ativo boolean, "
            + "sort_order int)",
        "CREATE TABLE planos_credito (id bigint PRIMARY KEY, nome text, descricao text, "
            + "creditos int, valor numeric, ativo boolean)",
        "CREATE TABLE pagamentos_mp (id bigint PRIMARY KEY, usuario_id bigint, plano_id bigint, "
            + "provider text, metodo_pagamento text, status text, status_efetivo text, "
            + "provider_payment_id text, mp_payment_id text, txid text, valor numeric, "
            + "creditos int, creditado boolean, expiracao timestamptz, criado_em timestamptz, "
            + "atualizado_em timestamptz)",
        "CREATE TABLE feature_ativacao (id bigint PRIMARY KEY, usuario_id bigint, "
            + "anuncio_id bigint, codigo text, creditos_cobrados int, status text, "
            + "ativado_em timestamptz, expira_em timestamptz)",
        "CREATE TABLE creditos_usuario (id bigint PRIMARY KEY, usuario_id bigint, saldo int)",
        "CREATE TABLE historico_creditos (id bigint PRIMARY KEY, usuario_id bigint, "
            + "quantidade int)",
        "CREATE TABLE usuario_favoritos (usuario_id bigint, anuncio_id bigint)");
    comandos.forEach(jdbc::execute);
  }

  private static void inserirFotografia(JdbcTemplate jdbc) {
    jdbc.update(
        "INSERT INTO estado (id, uf, nome, nome_normalizado, slug) "
            + "VALUES (1, 'QA', 'Estado QA', 'estado qa', 'estado-qa')");
    jdbc.update(
        "INSERT INTO cidade (id, estado_id, nome, nome_normalizado, slug) "
            + "VALUES (1, 1, 'Cidade QA', 'cidade qa', 'cidade-qa')");
    jdbc.update(
        "INSERT INTO bairro (id, cidade_id, nome, nome_normalizado, slug) "
            + "VALUES (1, 1, 'Bairro QA', 'bairro qa', 'bairro-qa')");
    jdbc.update(
        "INSERT INTO usuarios "
            + "(id, username, email, nome_completo, cpf, telefone, data_nascimento, status, "
            + "role, criado_em, verificado, is_verificado) "
            + "VALUES (1, 'admin-qa', null, 'Admin QA', null, null, null, "
            + "'ATIVO', 'ADMIN', ?, true, true)",
        CAPTURADO_EM.minusDays(10));
    jdbc.update(
        "INSERT INTO usuarios_staging "
            + "(id, username, email, nome_completo, cpf, telefone, data_nascimento, status, "
            + "role, criado_em, verificado, is_verificado) VALUES "
            + "(1, 'admin-qa', null, 'Admin QA', null, null, null, "
            + "'ATIVO', 'ADMIN', ?, true, true), "
            + "(2, 'staging-only', null, 'Staging QA', null, null, null, "
            + "'ATIVO', 'USER', ?, false, false)",
        CAPTURADO_EM.minusDays(10),
        CAPTURADO_EM.minusDays(2));
  }

  private static ManifestoMidiaFaseCinco manifesto() {
    return new ManifestoMidiaFaseCinco(List.of(new Item(
        "temporario-descartado",
        EntidadeTipo.TEMPORARIO,
        null,
        null,
        null,
        null,
        null,
        Finalidade.TEMPORARIO,
        TipoMidia.FOTO,
        Visibilidade.PRIVADA,
        EstadoModeracao.NAO_APLICAVEL,
        false,
        false,
        0,
        "image/jpeg",
        0,
        null,
        new Origem(
            TipoOrigem.OBJECT_STORAGE,
            StorageArea.PRIVATE_MEDIA,
            "legado/temporario/descartado.jpg"),
        null,
        Decisao.DESCARTAR,
        "FORA_DO_ESCOPO_CANONICO")));
  }

  private static MigracaoIntegralStorageConfiguration.DestinoProperties destinoProperties() {
    var properties = new MigracaoIntegralStorageConfiguration.DestinoProperties();
    properties.setPublicMediaBucket("public-target");
    properties.setPrivateMediaBucket("private-target");
    properties.setDocumentBucket("document-target");
    return properties;
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
