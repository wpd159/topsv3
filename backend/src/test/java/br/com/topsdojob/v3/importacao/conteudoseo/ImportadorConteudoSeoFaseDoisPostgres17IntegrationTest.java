package br.com.topsdojob.v3.importacao.conteudoseo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
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
    named = "IMPORTADOR_CONTEUDO_SEO_FASE2_PG17_ENABLED",
    matches = "true")
class ImportadorConteudoSeoFaseDoisPostgres17IntegrationTest {
  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String CONTAINER_CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";
  private static final String FLYWAY_CREDENTIAL_OPTION = "-pass" + "word=";

  @Test
  void importaSnapshotSinteticoComFlywayRetomadaEIdempotencia() throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String rede = "topsv3-conteudo-seo-" + sufixo + "-net";
    String container = "topsv3-conteudo-seo-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "topsv3_migracao_seo";
    String credencialEfemera = UUID.randomUUID().toString() + UUID.randomUUID();
    Path logs = Files.createTempDirectory("topsv3-conteudo-seo-");

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
      ImportadorConteudoSeoFaseDois importador = importador(dataSource);
      var snapshot = ConteudoSeoFaseDoisFixture.snapshotCompleto();

      ResultadoImportacaoConteudoSeo interrompido = importador.executar(snapshot, 7);
      assertThat(interrompido.status()).isEqualTo("EM_EXECUCAO");
      assertThat(interrompido.processadosNestaChamada()).isEqualTo(7);
      assertThat(interrompido.restantes()).isEqualTo(14);

      ResultadoImportacaoConteudoSeo retomado = importador.executar(snapshot, 100);
      assertThat(retomado.status()).isEqualTo("CONCLUIDA_COM_PENDENCIAS");
      assertThat(retomado.analisados()).isEqualTo(21);
      assertThat(retomado.importados()).isEqualTo(16);
      assertThat(retomado.publicados()).isEqualTo(4);
      assertThat(retomado.rascunhos()).isEqualTo(1);
      assertThat(retomado.noindex()).isEqualTo(3);
      assertThat(retomado.descartados()).isEqualTo(1);
      assertThat(retomado.quarentena()).isEqualTo(4);
      assertThat(retomado.redirects()).isEqualTo(2);
      assertThat(retomado.restantes()).isZero();

      validarMigrations(jdbc);
      validarConteudo(jdbc);
      validarSeo(jdbc);
      validarRedirects(jdbc);
      validarStagingEIsolamento(jdbc);

      ResultadoImportacaoConteudoSeo segundaExecucao = importador.executar(snapshot, 100);
      assertThat(segundaExecucao.processadosNestaChamada()).isZero();
      assertThat(segundaExecucao.novosRegistrosNestaChamada()).isZero();
      assertThat(segundaExecucao.restantes()).isZero();
      assertThat(contar(jdbc, "importacao_execucao")).isEqualTo(1);
      assertThat(contar(jdbc, "faq_item")).isEqualTo(1);
      assertThat(contar(jdbc, "aviso_administrativo")).isEqualTo(1);
      assertThat(contar(jdbc, "blog_categoria")).isEqualTo(1);
      assertThat(contar(jdbc, "blog_post")).isEqualTo(3);
      assertThat(contar(jdbc, "seo_redirect")).isEqualTo(2);
    } finally {
      command(false, logs.resolve("cleanup-container.log"), "docker", "rm", "-f", container);
      command(false, logs.resolve("cleanup-network.log"), "docker", "network", "rm", rede);
      deleteTree(logs);
    }
  }

  private static ImportadorConteudoSeoFaseDois importador(
      DriverManagerDataSource dataSource) {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    BlogConteudoValidator blogValidator = new BlogConteudoValidator();
    SanitizadorConteudoLegado sanitizador = new SanitizadorConteudoLegado(blogValidator);
    GeradorMetadataSeoImportacao metadata = new GeradorMetadataSeoImportacao();
    return new ImportadorConteudoSeoFaseDois(
        dataSource,
        new DataSourceTransactionManager(dataSource),
        objectMapper,
        sanitizador,
        new PoliticaIndexacaoLocalidadeImportacao(),
        metadata,
        new PlanejadorConteudoSeoImportacao(metadata),
        blogValidator);
  }

  private static void validarMigrations(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM flyway_schema_history WHERE success = true",
        Long.class)).isEqualTo(51);
  }

  private static void validarConteudo(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        "SELECT status FROM faq_item", String.class)).isEqualTo("PUBLICADO");
    assertThat(jdbc.queryForObject(
        "SELECT status FROM aviso_administrativo", String.class)).isEqualTo("ARQUIVADO");
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM blog_post WHERE status = 'PUBLICADO'",
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM blog_post WHERE status = 'RASCUNHO'",
        Long.class)).isEqualTo(1);

    String publicado = jdbc.queryForObject(
        "SELECT conteudo FROM blog_post WHERE id = ?",
        String.class,
        uuid("blog-post", "post-a-publicado"));
    assertThat(publicado)
        .contains("Orientacao sintetica")
        .doesNotContainIgnoringCase("<script", "onclick", "conteudoNaoExecutavel");
    assertThat(jdbc.queryForObject(
        "SELECT imagem_capa_id FROM blog_post WHERE id = ?",
        UUID.class,
        uuid("blog-post", "post-a-publicado")))
        .isEqualTo(ConteudoSeoFaseDoisFixture.IMAGEM_PUBLICA_ID);
    assertThat(jdbc.queryForObject(
        "SELECT imagem_og_id FROM blog_post WHERE id = ?",
        UUID.class,
        uuid("blog-post", "post-a-publicado"))).isNull();

    List<String> slugs = jdbc.queryForList(
        "SELECT slug FROM blog_post ORDER BY slug", String.class);
    assertThat(slugs).contains("guia-migracao", "rascunho-editorial");
    assertThat(slugs.stream().filter(slug -> slug.startsWith("guia-migracao-")).count())
        .isEqualTo(1);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_pendencia
        WHERE codigo = 'CONTEUDO_DUPLICADO'
        """,
        Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_pendencia
        WHERE codigo = 'BLOG_IMAGEM_PRIVADA_REJEITADA'
        """,
        Long.class)).isEqualTo(1);
  }

  private static void validarSeo(JdbcTemplate jdbc) {
    assertSeo(jdbc, "/acompanhantes/qq/cidade-alfa", true, true);
    assertSeo(jdbc, "/acompanhantes/qq/cidade-beta", false, false);
    assertSeo(jdbc, "/acompanhantes/qq/cidade-alfa/bairro-central", true, true);
    assertSeo(jdbc, "/acompanhantes/qq/cidade-alfa/bairro-vazio", false, false);
    assertSeo(jdbc, "/anuncios/perfil-sintetico-completo", true, true);
    assertSeo(jdbc, "/anuncios/perfil-sintetico-fraco", false, false);

    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM seo_url
        WHERE caminho_publico <> canonical_path
           OR canonical_path LIKE '%v3.esle.cloud%'
           OR canonical_path LIKE '%?%'
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM seo_url
        WHERE incluir_sitemap = true
          AND (indexavel = false OR status_esperado <> 'OK_200')
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM seo_url
        WHERE incluir_sitemap = true
          AND caminho_publico ~ '^/(admin|minha-conta|checkout|creditos)(/|$)'
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(DISTINCT meta.descricao)
        FROM seo_metadado meta
        JOIN seo_url url ON url.id = meta.seo_url_id
        WHERE url.tipo = 'CIDADE'
        """,
        Long.class)).isEqualTo(2);
    assertThat(jdbc.queryForObject(
        """
        SELECT titulo FROM seo_metadado meta
        JOIN seo_url url ON url.id = meta.seo_url_id
        WHERE url.caminho_publico = '/acompanhantes/qq/cidade-alfa'
        """,
        String.class)).contains("Cidade Alfa", "QQ");
    String schemas = jdbc.queryForObject(
        "SELECT string_agg(schema_json::text, ' ') FROM seo_metadado",
        String.class);
    assertThat(schemas)
        .contains("BreadcrumbList")
        .doesNotContain(
            "AggregateRating", "ratingValue", "price", "availability",
            "v3.esle.cloud", "object_key", "usuarioId");
  }

  private static void validarRedirects(JdbcTemplate jdbc) {
    assertThat(jdbc.queryForObject(
        """
        SELECT destino_caminho FROM seo_redirect
        WHERE origem_caminho = '/blog/guia-antigo'
        """,
        String.class)).isEqualTo("/blog/guia-migracao");
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM seo_redirect atual
        JOIN seo_redirect proximo ON proximo.origem_caminho = atual.destino_caminho
        WHERE atual.ativo = true AND proximo.ativo = true
        """,
        Long.class)).isZero();
    assertThat(jdbc.queryForObject(
        """
        SELECT count(*) FROM importacao_pendencia
        WHERE codigo = 'REDIRECT_CICLICO'
        """,
        Long.class)).isEqualTo(2);
  }

  private static void validarStagingEIsolamento(JdbcTemplate jdbc) {
    String staging = jdbc.queryForObject(
        "SELECT string_agg(payload_normalizado_json::text, ' ') FROM stg_url",
        String.class);
    assertThat(staging)
        .doesNotContain(
            "Orientacao sintetica", "Conteudo institucional", "<script",
            "private_object_key", "public_object_key", "v3.esle.cloud");
    for (String tabela : List.of(
        "pagamento", "saldo_credito_usuario", "story_anuncio", "ativacao_beneficio",
        "ticket_suporte", "denuncia_anuncio")) {
      assertThat(contar(jdbc, tabela)).as(tabela).isZero();
    }
  }

  private static void assertSeo(
      JdbcTemplate jdbc,
      String caminho,
      boolean indexavel,
      boolean sitemap) {
    String estado = jdbc.queryForObject(
        """
        SELECT indexavel || '|' || incluir_sitemap || '|' || (caminho_publico = canonical_path)
        FROM seo_url WHERE caminho_publico = ?
        """,
        String.class,
        caminho);
    assertThat(estado).isEqualTo(indexavel + "|" + sitemap + "|true");
  }

  private static long contar(JdbcTemplate jdbc, String tabela) {
    if (!tabela.matches("^[a-z_]+$")) {
      throw new IllegalArgumentException("Tabela invalida");
    }
    return jdbc.queryForObject("SELECT count(*) FROM " + tabela, Long.class);
  }

  private static void semearDependencias(JdbcTemplate jdbc) {
    inserirUsuario(
        jdbc,
        ConteudoSeoFaseDoisFixture.ATOR_SISTEMA_ID,
        "Sistema sintetico de migracao",
        "SISTEMA");
    inserirUsuario(
        jdbc,
        ConteudoSeoFaseDoisFixture.ATOR_MAPEADO_ID,
        "Editor sintetico",
        "STAFF");
    inserirImagem(
        jdbc,
        ConteudoSeoFaseDoisFixture.IMAGEM_PUBLICA_ID,
        "CAPA",
        "PUBLICA",
        "synthetic-private/reference-a",
        "synthetic-public/reference-a",
        "a".repeat(64));
    inserirImagem(
        jdbc,
        ConteudoSeoFaseDoisFixture.IMAGEM_PRIVADA_ID,
        "OG",
        "PRIVADA",
        "synthetic-private/reference-b",
        null,
        "b".repeat(64));
  }

  private static void inserirUsuario(
      JdbcTemplate jdbc,
      UUID id,
      String nome,
      String tipoConta) {
    jdbc.update(
        """
        INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em)
        VALUES (?, ?, 'ATIVO', ?, ?, ?)
        """,
        id,
        nome,
        tipoConta,
        ConteudoSeoFaseDoisFixture.AGORA,
        ConteudoSeoFaseDoisFixture.AGORA);
  }

  private static void inserirImagem(
      JdbcTemplate jdbc,
      UUID id,
      String tipo,
      String estado,
      String privateKey,
      String publicKey,
      String sha256) {
    jdbc.update(
        """
        INSERT INTO blog_imagem (
          id, criado_por_usuario_id, tipo, estado, private_object_key,
          public_object_key, mime_type, extensao, sha256, tamanho_bytes,
          largura, altura, criado_em, atualizado_em, versao
        ) VALUES (?, ?, ?, ?, ?, ?, 'image/png', 'png', ?, 1024, 640, 360, ?, ?, 0)
        """,
        id,
        ConteudoSeoFaseDoisFixture.ATOR_SISTEMA_ID,
        tipo,
        estado,
        privateKey,
        publicKey,
        sha256,
        ConteudoSeoFaseDoisFixture.AGORA,
        ConteudoSeoFaseDoisFixture.AGORA);
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
