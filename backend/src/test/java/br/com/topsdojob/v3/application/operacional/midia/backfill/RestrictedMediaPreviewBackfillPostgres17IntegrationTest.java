package br.com.topsdojob.v3.application.operacional.midia.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.platform.health.HealthController;
import jakarta.servlet.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.RestController;

@EnabledIfEnvironmentVariable(
    named = "PREVIEW_BACKFILL_POSTGRES17_ENABLED",
    matches = "true")
class RestrictedMediaPreviewBackfillPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String SUFFIX =
      UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  private static final String NETWORK = "topsv3-preview-context-" + SUFFIX + "-net";
  private static final String CONTAINER = "topsv3-preview-context-" + SUFFIX + "-pg17";
  private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
  private static int port;

  @BeforeAll
  static void startPostgres() throws Exception {
    command("docker", "network", "create", NETWORK);
    try {
      command(
          Map.of("POSTGRES_PASSWORD", CREDENTIAL),
          "docker", "run", "--pull=never", "-d", "--name", CONTAINER,
          "--network", NETWORK,
          "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=topsv3_preview_context",
          "-e", "POSTGRES_USER=topsv3test",
          "-e", "POSTGRES_PASSWORD",
          "postgres:17-alpine");
      awaitPostgres();
      migrate();
      port = mappedPort();
    } catch (Exception exception) {
      cleanup();
      throw exception;
    }
  }

  @AfterAll
  static void cleanup() throws Exception {
    commandIgnoringFailure("docker", "rm", "-f", CONTAINER);
    commandIgnoringFailure("docker", "network", "rm", NETWORK);
  }

  @Test
  void preservaWebSecurityEExecutaPlanEmContextoMinimoNaoWeb() throws Exception {
    validarContextoWebCompleto();
    validarContextoMinimo();
    executarPlanReadOnly();
  }

  private void validarContextoWebCompleto() throws Exception {
    SpringApplication application = new SpringApplication(TopsDoJobBackendApplication.class);
    application.setWebApplicationType(WebApplicationType.SERVLET);
    Map<String, Object> properties = commonProperties();
    properties.put("server.port", "0");
    application.setDefaultProperties(properties);
    application.addInitializers(context -> ((GenericApplicationContext) context)
        .registerBean(ObjectStorage.class, () -> mock(ObjectStorage.class)));

    try (ConfigurableApplicationContext context = application.run()) {
      assertThat(context).isInstanceOf(WebServerApplicationContext.class);
      assertThat(context.getBeansOfType(AuthenticationManager.class)).hasSize(1);
      assertThat(context.getBeansOfType(SecurityFilterChain.class)).isNotEmpty();

      int webPort = ((WebServerApplicationContext) context).getWebServer().getPort();
      HttpResponse<Void> response = HttpClient.newHttpClient().send(
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:" + webPort + "/api/admin/visao-geral"))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.discarding());
      assertThat(response.statusCode()).isIn(401, 403);
    }
  }

  private void validarContextoMinimo() {
    ObjectStorageInventory inventory = mock(ObjectStorageInventory.class);
    SpringApplication application = RestrictedMediaPreviewBackfillBootstrap.application();
    application.setDefaultProperties(commonProperties());
    application.addInitializers(context -> ((GenericApplicationContext) context)
        .registerBean(ObjectStorageInventory.class, () -> inventory));

    try (ConfigurableApplicationContext context = application.run(
        RestrictedMediaPreviewBackfillBootstrap.BOOTSTRAP_ARGUMENT,
        "--app.restricted-media-preview-reconciliation.enabled=false")) {
      assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
      assertThat(context.getBeansOfType(AuthenticationManager.class)).isEmpty();
      assertThat(context.getBeansOfType(SecurityFilterChain.class)).isEmpty();
      assertThat(context.getBeansOfType(HealthController.class)).isEmpty();
      assertThat(context.getBeansOfType(Filter.class)).isEmpty();
      assertThat(context.getBeanNamesForAnnotation(RestController.class)).isEmpty();
      assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
      assertThat(context.getBeansOfType(ObjectStorage.class)).isEmpty();
      assertThat(context.getBeanNamesForType(Repository.class)).hasSize(2);
      assertThat(context.getBean(AnuncioMidiaRepository.class)).isNotNull();
      assertThat(context.getBean(ArquivoMidiaRepository.class)).isNotNull();
      assertThat(context.getBean(MidiaRestritaRegularizacaoService.class)).isNotNull();
      verifyNoMoreInteractions(inventory);
    }
  }

  private void executarPlanReadOnly() throws Exception {
    JdbcTemplate jdbc = jdbc();
    long arquivosAntes = jdbc.queryForObject("SELECT count(*) FROM arquivo_midia", Long.class);
    long vinculosAntes = jdbc.queryForObject("SELECT count(*) FROM anuncio_midia", Long.class);
    long flywayAntes = jdbc.queryForObject(
        "SELECT count(*) FROM flyway_schema_history", Long.class);
    ObjectStorageInventory inventory = mock(ObjectStorageInventory.class);
    when(inventory.list(any(), any(), isNull(), anyInt()))
        .thenReturn(new StoredObjectPage(java.util.List.of(), null, false));
    Path report = Files.createTempFile("topsv3-preview-plan-", ".tsv");

    try {
      SpringApplication application = RestrictedMediaPreviewBackfillBootstrap.application();
      application.setDefaultProperties(commonProperties());
      application.addInitializers(context -> ((GenericApplicationContext) context)
          .registerBean(ObjectStorageInventory.class, () -> inventory));
      ConfigurableApplicationContext context = application.run(
          RestrictedMediaPreviewBackfillBootstrap.BOOTSTRAP_ARGUMENT,
          "--app.restricted-media-preview-reconciliation.enabled=true",
          "--app.restricted-media-preview-reconciliation.mode=PLAN",
          "--app.restricted-media-preview-reconciliation.apply-confirmed=false",
          "--app.restricted-media-preview-reconciliation.batch-size=200",
          "--app.restricted-media-preview-reconciliation.report-path="
              + report.toAbsolutePath().normalize());

      assertThat(context.isActive()).isFalse();
      assertThat(Files.readString(report)).startsWith(
          "arquivo_hash\tchave_hash\tclassificacao\n");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM arquivo_midia", Long.class))
          .isEqualTo(arquivosAntes);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM anuncio_midia", Long.class))
          .isEqualTo(vinculosAntes);
      assertThat(jdbc.queryForObject(
          "SELECT count(*) FROM flyway_schema_history", Long.class))
          .isEqualTo(flywayAntes);
      verify(inventory).list(
          StorageArea.PUBLIC_MEDIA,
          "hml/midias-aprovadas/restritas-borradas/v1/",
          null,
          1_000);
      verifyNoMoreInteractions(inventory);
    } finally {
      Files.deleteIfExists(report);
    }
  }

  private static Map<String, Object> commonProperties() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("spring.datasource.url", jdbcUrl());
    properties.put("spring.datasource.username", "topsv3test");
    properties.put("spring.datasource.password", CREDENTIAL);
    properties.put("spring.flyway.enabled", "false");
    properties.put("spring.jpa.hibernate.ddl-auto", "validate");
    properties.put("spring.jpa.open-in-view", "false");
    properties.put("spring.task.scheduling.enabled", "false");
    properties.put("app.event.hash-salt", "preview-context-test-only-salt");
    properties.put("app.age-gate.signing-value", "preview-context-test-only-signing-value");
    properties.put("app.storage.r2.enabled", "false");
    properties.put("app.outbox.email.enabled", "false");
    properties.put("efi.pix.enabled", "false");
    properties.put("efi.pix.reconciliation-enabled", "false");
    properties.put("efi.pix.webhook-registration-enabled", "false");
    properties.put("logging.level.root", "ERROR");
    return properties;
  }

  private static JdbcTemplate jdbc() {
    return new JdbcTemplate(new DriverManagerDataSource(
        jdbcUrl(), "topsv3test", CREDENTIAL));
  }

  private static String jdbcUrl() {
    return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_preview_context";
  }

  private static void migrate() throws Exception {
    command(
        Map.of("FLYWAY_PASSWORD", CREDENTIAL),
        "docker", "run", "--pull=never", "--rm", "--network", NETWORK,
        "-e", "FLYWAY_PASSWORD",
        "-v", MIGRATIONS + ":/flyway/sql:ro",
        "flyway/flyway:12.10.0",
        "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_preview_context",
        "-user=topsv3test",
        "-locations=filesystem:/flyway/sql",
        "migrate");
  }

  private static void awaitPostgres() throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandIgnoringFailure(
          Map.of("PGPASSWORD", CREDENTIAL),
          "docker", "exec", "-e", "PGPASSWORD", CONTAINER,
          "pg_isready", "--host", "127.0.0.1",
          "--username", "topsv3test", "--dbname", "topsv3_preview_context") == 0) {
        return;
      }
      Thread.sleep(500L);
    }
    throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
  }

  private static int mappedPort() throws Exception {
    String output = command("docker", "port", CONTAINER, "5432/tcp").trim();
    return Integer.parseInt(output.substring(output.lastIndexOf(':') + 1));
  }

  private static String command(String... args) throws Exception {
    return command(Map.of(), args);
  }

  private static String command(Map<String, String> environment, String... args)
      throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(String.join(" ", args) + " falhou: " + output);
    }
    return output;
  }

  private static int commandIgnoringFailure(String... args) throws Exception {
    return commandIgnoringFailure(Map.of(), args);
  }

  private static int commandIgnoringFailure(
      Map<String, String> environment,
      String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    builder.environment().putAll(environment);
    Process process = builder.start();
    process.getInputStream().readAllBytes();
    return process.waitFor();
  }
}
