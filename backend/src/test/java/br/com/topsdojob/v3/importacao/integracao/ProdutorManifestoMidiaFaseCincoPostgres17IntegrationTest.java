package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.DestinoProperties;
import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.FonteProperties;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Decisao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.EntidadeTipo;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Finalidade;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(
    named = "MIGRACAO_MANIFESTO_PG17_ENABLED",
    matches = "true")
class ProdutorManifestoMidiaFaseCincoPostgres17IntegrationTest {

  private static final String SNAPSHOT = "a".repeat(64);
  private static final OffsetDateTime INSTANTE = OffsetDateTime.parse("2026-08-14T12:00:00Z");
  private static final String CREDENTIAL_ENV = "POSTGRES_" + "PASSWORD";

  @Test
  void snapshotRestauradoProduzCandidatosManifestoDeterministicoSemHttpNaTransacao()
      throws Exception {
    String sufixo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String container = "topsv3-manifesto-" + sufixo + "-pg17";
    String usuario = "migracaoqa";
    String banco = "legacy_media_qa";
    String credencial = UUID.randomUUID().toString() + UUID.randomUUID();
    Path temporario = Files.createTempDirectory("topsv3-manifesto-pg17-");

    command(true, temporario.resolve("container.log"),
        "docker", "run", "--pull=never", "-d", "--name", container,
        "-p", "127.0.0.1::5432",
        "-e", "POSTGRES_DB=" + banco,
        "-e", "POSTGRES_USER=" + usuario,
        "-e", CREDENTIAL_ENV + "=" + credencial,
        "postgres:17-alpine");
    try {
      aguardarPostgres(container, usuario, banco, temporario);
      int porta = portaPublicada(container, temporario);
      DriverManagerDataSource dataSource = new DriverManagerDataSource(
          "jdbc:postgresql://127.0.0.1:" + porta + "/" + banco,
          usuario,
          credencial);
      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      criarSchema(jdbc);
      inserirFixtures(jdbc);

      FonteProperties fonteProperties = fonteProperties();
      RepositorioCandidatosMidiaLegada adapter = new RepositorioCandidatosMidiaLegada(
          dataSource,
          new DataSourceTransactionManager(dataSource),
          fonteProperties);
      var descritores = adapter.listar();
      assertThat(descritores).extracting(item -> item.entidadeTipo())
          .contains(
              EntidadeTipo.ANUNCIO,
              EntidadeTipo.REVISAO_ANUNCIO,
              EntidadeTipo.KYC,
              EntidadeTipo.EDITORIAL);
      assertThat(descritores).extracting(item -> item.finalidade())
          .contains(
              Finalidade.CAPA,
              Finalidade.GALERIA,
              Finalidade.VIDEO,
              Finalidade.REVISAO,
              Finalidade.KYC_IDENTIDADE,
              Finalidade.BLOG_CAPA);
      assertThat(descritores)
          .filteredOn(item -> !item.referenciaValida())
          .allSatisfy(item -> assertThat(item.origem().localizador())
              .startsWith("quarentena/referencia-invalida/")
              .doesNotContain("?"));

      AtomicBoolean transacaoDuranteStorage = new AtomicBoolean();
      FonteMidiaMigracao fonte = origem -> {
        transacaoDuranteStorage.compareAndSet(
            false, TransactionSynchronizationManager.isActualTransactionActive());
        return objeto(origem.localizador());
      };
      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      ProdutorManifestoMidiaFaseCinco produtor = new ProdutorManifestoMidiaFaseCinco(
          adapter, fonte, destinoProperties(), mapper);
      Path primeiro = temporario.resolve("manifesto-a.json");
      Path segundo = temporario.resolve("manifesto-b.json");
      var resultadoA = produtor.produzir(parametros("execucao-a", INSTANTE, primeiro));
      var resultadoB = produtor.produzir(parametros(
          "execucao-b", INSTANTE.plusHours(1), segundo));
      ArquivoManifestoMidiaFaseCinco artefato =
          new RepositorioManifestoMidiaFaseCinco(mapper).carregar(primeiro);

      assertThat(transacaoDuranteStorage).isFalse();
      assertThat(resultadoB.fingerprint()).isEqualTo(resultadoA.fingerprint());
      assertThat(artefato.manifesto().itens())
          .filteredOn(item -> item.decisao() == Decisao.IMPORTAR)
          .isNotEmpty();
      assertThat(artefato.manifesto().itens())
          .filteredOn(item -> item.entidadeTipo() == EntidadeTipo.KYC
              && item.proprietarioV3Id() == null)
          .allSatisfy(item -> assertThat(item.decisao()).isEqualTo(Decisao.QUARENTENA));
      assertThat(resultadoA.storage()).containsEntry("MUTACOES", 0L);
    } finally {
      command(false, temporario.resolve("cleanup-container.log"),
          "docker", "rm", "-f", "-v", container);
      deleteTree(temporario);
    }
  }

  private static void criarSchema(JdbcTemplate jdbc) {
    List.of(
        "CREATE TABLE usuarios (id bigint PRIMARY KEY)",
        "CREATE TABLE anuncios (id bigint PRIMARY KEY, usuario_id bigint, status text, "
            + "content_classification text)",
        "CREATE TABLE anuncio_fotos (anuncio_id bigint, url_foto text)",
        "CREATE TABLE anuncio_videos (anuncio_id bigint, url_video text)",
        "CREATE TABLE protected_media_assets (id bigint PRIMARY KEY, active boolean, "
            + "sort_order int, anuncio_id bigint, media_type text, storage_mode text, "
            + "content_type text, original_storage_ref text, legacy_original_url text, "
            + "preview_public_url text)",
        "CREATE TABLE anuncio_revisions (id bigint PRIMARY KEY, anuncio_id bigint, status text)",
        "CREATE TABLE anuncio_revision_media (id bigint PRIMARY KEY, revision_id bigint, "
            + "sort_order int, media_type text, source_type text, content_type text, "
            + "storage_ref text, preview_public_url text, public_url text)",
        "CREATE TABLE usuario_documentos (usuario_id bigint, documento_url text)",
        "CREATE TABLE blog_posts (id bigint PRIMARY KEY, created_by_user_id bigint, "
            + "status text, imagem_url text, og_image_url text)",
        "CREATE TABLE site_images (id bigint PRIMARY KEY, path text, url text, type text)")
        .forEach(jdbc::execute);
  }

  private static void inserirFixtures(JdbcTemplate jdbc) {
    jdbc.execute("INSERT INTO usuarios VALUES (1), (2)");
    jdbc.execute("INSERT INTO anuncios VALUES "
        + "(10, 1, 'ATIVO', 'LIVRE'), (11, 1, 'ATIVO', 'RESTRITA_18')");
    jdbc.execute("INSERT INTO protected_media_assets VALUES "
        + "(100, true, 0, 10, 'IMAGE', 'LEGACY_PUBLIC_PROXY', 'image/jpeg', "
        + "'legacy/cache/foto.jpg', 'https://media.example.test/base/anuncios/capa.jpg', null), "
        + "(101, true, 1, 11, 'VIDEO', 'PRIVATE_R2', 'video/mp4', "
        + "'r2://private-source/legacy/anuncios/video.mp4', null, null)");
    jdbc.execute("INSERT INTO anuncio_fotos VALUES "
        + "(10, 'https://media.example.test/base/anuncios/capa.jpg'), "
        + "(10, 'https://media.example.test/base/anuncios/galeria.png')");
    jdbc.execute("INSERT INTO anuncio_videos VALUES "
        + "(11, 'https://media.example.test/base/anuncios/video-publico.mp4')");
    jdbc.execute("INSERT INTO anuncio_revisions VALUES (200, 10, 'APROVADA')");
    jdbc.execute("INSERT INTO anuncio_revision_media VALUES "
        + "(201, 200, 0, 'IMAGE', 'STAGED_PRIVATE_PROTECTED', 'image/jpeg', "
        + "'legacy/revisoes/foto.jpg', null, null)");
    jdbc.execute("INSERT INTO usuario_documentos VALUES "
        + "(1, 'r2://document-source/legacy/kyc/canonico.pdf'), "
        + "(1, 'r2://document-source/legacy/kyc/cruzado.pdf'), "
        + "(2, 'r2://document-source/legacy/kyc/cruzado.pdf'), "
        + "(1, 'https://media.example.test/base/kyc/assinado.jpg?parametro=qa')");
    jdbc.execute("INSERT INTO blog_posts VALUES "
        + "(300, 1, 'PUBLICADO', 'https://media.example.test/base/blog/capa.jpg', null)");
    jdbc.execute("INSERT INTO site_images VALUES "
        + "(400, 'editorial/sem-owner.png', "
        + "'https://media.example.test/base/editorial/sem-owner.png', 'EDITORIAL')");
  }

  private static StoredObject objeto(String chave) {
    if (chave.endsWith(".pdf")) {
      return new StoredObject("%PDF-qa".getBytes(StandardCharsets.US_ASCII), "application/pdf");
    }
    if (chave.endsWith(".mp4")) {
      byte[] bytes = new byte[16];
      bytes[4] = 'f';
      bytes[5] = 't';
      bytes[6] = 'y';
      bytes[7] = 'p';
      bytes[8] = 'i';
      bytes[9] = 's';
      bytes[10] = 'o';
      bytes[11] = 'm';
      return new StoredObject(bytes, "video/mp4");
    }
    if (chave.endsWith(".png")) {
      return new StoredObject(
          new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");
    }
    return new StoredObject(
        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}, "image/jpeg");
  }

  private static FonteProperties fonteProperties() {
    FonteProperties properties = new FonteProperties();
    properties.setPublicMediaBucket("public-source");
    properties.setPrivateMediaBucket("private-source");
    properties.setDocumentBucket("document-source");
    properties.setPublicBaseUrl("https://media.example.test/base");
    return properties;
  }

  private static DestinoProperties destinoProperties() {
    DestinoProperties properties = new DestinoProperties();
    properties.setPublicMediaBucket("public-target");
    properties.setPrivateMediaBucket("private-target");
    properties.setDocumentBucket("document-target");
    return properties;
  }

  private static ProdutorManifestoMidiaFaseCinco.Parametros parametros(
      String execucao,
      OffsetDateTime instante,
      Path saida) {
    return new ProdutorManifestoMidiaFaseCinco.Parametros(
        "TOPSDOJOB_LEGADO", SNAPSHOT, execucao, instante, saida);
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
      throw new IllegalStateException("comando descartavel falhou");
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
