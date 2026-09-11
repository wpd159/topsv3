package br.com.topsdojob.v3.application.operacional.midia.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService.EstadoCommit;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectMetadata;
import br.com.topsdojob.v3.infrastructure.storage.StoredObjectPage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Real local commits/rollbacks; only the read-only object inventory is synthetic. */
@EnabledIfEnvironmentVariable(named = "PREVIEW_BACKFILL_POSTGRES17_ENABLED", matches = "true")
@Execution(ExecutionMode.SAME_THREAD)
class RestrictedMediaPreviewBackfillApplyPostgres17IntegrationTest {

  private static final Path MIGRATIONS = Path.of(
      "src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
  private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  private static final String CONTAINER = "topsv3-preview-apply-" + SUFFIX + "-postgres";
  private static final String FAULT_SCHEMA = "preview_apply_fault_" + SUFFIX;
  private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
  private static final String PUBLIC_PREFIX = "hml/preview-apply/publicas/";
  private static final String PREVIEW_PREFIX = PUBLIC_PREFIX + "restritas-borradas/v1/";
  private static final String CHECKSUM = "a".repeat(64);
  private static final OffsetDateTime STAMP = OffsetDateTime.parse("2026-09-01T12:00:00Z");
  private static final AtomicReference<List<String>> INVENTORY_KEYS = new AtomicReference<>(List.of());
  private static ConfigurableApplicationContext context;
  private static MidiaRestritaRegularizacaoService service;
  private static JdbcTemplate jdbc;
  private static int port;
  private static PreviewBackfillOwnedDockerResources owned;
  @RegisterExtension
  static final FirstFailure firstFailure = new FirstFailure();

  private UUID userId;
  private UUID adId;
  private final List<Photo> photos = new ArrayList<>();

  @BeforeAll
  static void startOwnedPostgresAndDedicatedBootstrap() throws Throwable {
    owned = new PreviewBackfillOwnedDockerResources("topsv3-preview-apply-" + SUFFIX);
    try {
      owned.createNetwork();
      String postgresId = owned.createContainer("postgres", Map.of("POSTGRES_PASSWORD", CREDENTIAL),
          "-p", "127.0.0.1::5432",
          "-e", "POSTGRES_DB=topsv3_preview_apply", "-e", "POSTGRES_USER=topsv3test",
          "-e", "POSTGRES_PASSWORD", "postgres:17-alpine");
      command("docker", "start", postgresId);
      awaitPostgres();
      String flywayId = owned.createContainer("flyway", Map.of("FLYWAY_PASSWORD", CREDENTIAL),
          "-e", "FLYWAY_PASSWORD", "-v", MIGRATIONS + ":/flyway/sql:ro",
          "flyway/flyway:12.10.0",
          "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_preview_apply",
          "-user=topsv3test", "-locations=filesystem:/flyway/sql", "migrate");
      command("docker", "start", "--attach", flywayId);
      String mapping = command("docker", "port", CONTAINER, "5432/tcp").trim();
      port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
      jdbc = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl(), "topsv3test", CREDENTIAL));

      ObjectStorageInventory inventory = mock(ObjectStorageInventory.class);
      when(inventory.list(any(), any(), any(), anyInt())).thenAnswer(invocation -> {
        assertThat(invocation.getArgument(0, StorageArea.class)).isEqualTo(StorageArea.PUBLIC_MEDIA);
        assertThat(invocation.getArgument(1, String.class)).isEqualTo(PREVIEW_PREFIX);
        assertThat(invocation.getArgument(2, String.class)).isNull();
        assertThat(invocation.getArgument(3, Integer.class)).isEqualTo(1_000);
        return new StoredObjectPage(INVENTORY_KEYS.get().stream()
            .map(key -> new StoredObjectMetadata(key, 123L, "synthetic-preview", STAMP.toInstant()))
            .toList(), null, false);
      });
      SpringApplication application = RestrictedMediaPreviewBackfillBootstrap.application();
      application.setDefaultProperties(properties());
      application.addInitializers(applicationContext -> {
        // Environment variables must never redirect this test to a non-owned database or R2.
        applicationContext.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("preview-apply-owned-resources", properties()));
        ((GenericApplicationContext) applicationContext)
            .registerBean(ObjectStorageInventory.class, () -> inventory);
      });
      context = application.run(RestrictedMediaPreviewBackfillBootstrap.BOOTSTRAP_ARGUMENT,
          "--app.restricted-media-preview-reconciliation.enabled=false");
      service = context.getBean(MidiaRestritaRegularizacaoService.class);
      assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17.");
      assertThat(jdbc.queryForObject("select count(*) from arquivo_midia", Long.class)).isZero();
    } catch (Throwable failure) {
      firstFailure.remember(failure);
      throw cleanupWithPrimary(failure);
    }
  }

  @AfterAll
  static void cleanupOwnedResources() throws Throwable {
    Throwable cleanup = cleanupWithPrimary(null);
    if (cleanup != null) {
      Throwable original = firstFailure.failure.get();
      if (original != null && original != cleanup) original.addSuppressed(cleanup);
      // JUnit also reports cleanup separately; an earlier test/setup error is never replaced.
      throw cleanup;
    }
  }

  private static Throwable cleanupWithPrimary(Throwable primary) {
    ConfigurableApplicationContext closing = context;
    context = null;
    return PreviewBackfillOwnedDockerResources.cleanupPreserving(primary,
        () -> { if (closing != null) closing.close(); },
        () -> { if (owned != null) owned.close(); });
  }

  @BeforeEach
  void seedOnlyOwnedSyntheticRows() {
    userId = UUID.randomUUID();
    adId = UUID.randomUUID();
    jdbc.update("""
        insert into usuario(id,nome,status,tipo_conta,criado_em,atualizado_em,versao)
        values (?,'Pessoa sintetica preview','ATIVO','ANUNCIANTE',?,?,0)
        """, userId, STAMP, STAMP);
    jdbc.update("""
        insert into anuncio(id,usuario_id,slug,titulo,status,status_moderacao,categoria,
          publicado_em,ultima_publicacao_em,criado_em,atualizado_em,versao)
        values (?, ?, ?, 'Anuncio sintetico preview', 'PUBLICADO','APROVADO',
          'ACOMPANHANTE_FEMININA',?,?,?,?,0)
        """, adId, userId, "preview-apply-" + adId, STAMP, STAMP, STAMP, STAMP);
    photo(false, "RESTRITA_18");
    photo(false, "RESTRITA_18");
    photo(true, "RESTRITA_18");
    photo(false, "LIVRE"); // Outside the eligible universe; it must also stay byte-identical.
    completeInventory();
  }

  @AfterEach
  void removeOnlyOwnedSyntheticRows() throws Throwable {
    List<PreviewBackfillOwnedDockerResources.Action> actions = new ArrayList<>();
    actions.add(this::dropFault);
    if (adId != null) actions.add(() -> jdbc.update("delete from anuncio_midia where anuncio_id=?", adId));
    for (Photo photo : photos) actions.add(() -> jdbc.update("delete from arquivo_midia where id=?", photo.fileId()));
    if (adId != null) actions.add(() -> jdbc.update("delete from anuncio where id=?", adId));
    if (userId != null) actions.add(() -> jdbc.update("delete from usuario where id=?", userId));
    Throwable failure = PreviewBackfillOwnedDockerResources.cleanupPreserving(null,
        actions.toArray(PreviewBackfillOwnedDockerResources.Action[]::new));
    photos.clear();
    INVENTORY_KEYS.set(List.of());
    if (failure != null) throw failure;
  }

  @Test
  void aplicaSomenteCincoCamposEPreservaBytesDosRegularesEVinculos() {
    Snapshot before = snapshot();
    var plan = service.planejar();
    assertThat(plan.arquivos()).isEqualTo(3);
    assertThat(plan.disponiveis()).isEqualTo(3);
    assertThat(snapshot()).isEqualTo(before); // Real PLAN does not write.
    List<EstadoCommit> completion = new ArrayList<>();

    var result = service.aplicar(plan, 1, completion::add);

    assertThat(completion).containsExactly(EstadoCommit.COMMITTED);
    assertThat(result.atualizados()).isEqualTo(2);
    assertThat(result.inalterados()).isEqualTo(1);
    Snapshot after = snapshot();
    assertThat(after.nonPreview()).isEqualTo(before.nonPreview());
    assertThat(after.links()).isEqualTo(before.links());
    assertThat(after.ad()).isEqualTo(before.ad());
    assertThat(after.user()).isEqualTo(before.user());
    for (int index = 0; index < photos.size(); index++) {
      Photo photo = photos.get(index);
      if (index < 2) {
        assertThat(after.files().get(photo.fileId())).isNotEqualTo(before.files().get(photo.fileId()));
        assertCompleteMetadata(photo);
      } else {
        assertThat(after.files().get(photo.fileId())).isEqualTo(before.files().get(photo.fileId()));
      }
    }
    var validation = service.validarPersistencia(service.planejar(), 1);
    assertThat(validation.aprovada()).isTrue();
    assertThat(validation.disponiveis()).isEqualTo(3);
  }

  @Test
  void falhaRealNoSegundoLoteReverteTambemOPrimeiroEAceitaRetomadaNova() {
    Snapshot before = snapshot();
    var plan = service.planejar();
    installSecondUpdateFault();
    List<EstadoCommit> completion = new ArrayList<>();

    assertThatThrownBy(() -> service.aplicar(plan, 1, completion::add))
        .isInstanceOf(RuntimeException.class);

    assertThat(completion).containsExactly(EstadoCommit.ROLLED_BACK);
    // Sequence effects do not roll back: 2 proves the first real UPDATE preceded the error.
    assertThat(jdbc.queryForObject("select last_value from " + FAULT_SCHEMA + ".attempts", Long.class))
        .isEqualTo(2L);
    assertThat(snapshot()).isEqualTo(before);
    dropFault();
    List<EstadoCommit> resumedCompletion = new ArrayList<>();
    var resumed = service.aplicar(service.planejar(), 1, resumedCompletion::add);
    assertThat(resumed.atualizados()).isEqualTo(2);
    assertThat(resumedCompletion).containsExactly(EstadoCommit.COMMITTED);
    assertThat(service.validarPersistencia(service.planejar(), 1).aprovada()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"sha256", "origem", "status_arquivo", "vinculo", "arquivo_id", "novo_vinculo"})
  void divergenciaRealDepoisDoPlanBloqueiaApplySemEscritaDeMetadata(String mutation) {
    var plan = service.planejar();
    Photo target = photos.get(0);
    switch (mutation) {
      case "sha256" -> jdbc.update("update arquivo_midia set sha256=? where id=?",
          "b".repeat(64), target.fileId());
      case "origem" -> jdbc.update("update arquivo_midia set chave_objeto=chave_objeto || '-changed' where id=?",
          target.fileId());
      case "status_arquivo" -> jdbc.update("update arquivo_midia set status_arquivo='REJEITADO' where id=?",
          target.fileId());
      case "vinculo" -> jdbc.update("update anuncio_midia set status='PENDENTE' where id=?", target.linkId());
      case "arquivo_id" -> jdbc.update("update anuncio_midia set arquivo_midia_id=? where id=?",
          photos.get(2).fileId(), target.linkId());
      case "novo_vinculo" -> { photo(false, "RESTRITA_18"); completeInventory(); }
      default -> throw new AssertionError("Unknown synthetic mutation");
    }
    Snapshot afterConcurrentChange = snapshot();
    List<EstadoCommit> completion = new ArrayList<>();

    assertThatThrownBy(() -> service.aplicar(plan, 1, completion::add))
        .isInstanceOf(RuntimeException.class);

    assertThat(completion).containsExactly(EstadoCommit.ROLLED_BACK);
    assertThat(snapshot()).isEqualTo(afterConcurrentChange);
  }

  @Test
  void falhaDoNovoPlanAposCommitPreservaDadosERetomadaFazNoOp() {
    List<EstadoCommit> completion = new ArrayList<>();
    service.aplicar(service.planejar(), 1, completion::add);
    assertThat(completion).containsExactly(EstadoCommit.COMMITTED);
    Snapshot committed = snapshot();

    INVENTORY_KEYS.set(List.of(photos.get(0).previewKey(), photos.get(2).previewKey()));
    var incompleteNewPlan = service.planejar();
    assertThat(incompleteNewPlan.ausentes()).isEqualTo(1);
    assertThatThrownBy(() -> service.validarPersistencia(incompleteNewPlan, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(snapshot()).isEqualTo(committed); // A failed observer is not proof of no commit.

    completeInventory();
    List<EstadoCommit> resumedCompletion = new ArrayList<>();
    var resumed = service.aplicar(service.planejar(), 1, resumedCompletion::add);
    assertThat(resumedCompletion).containsExactly(EstadoCommit.COMMITTED);
    assertThat(resumed.atualizados()).isZero();
    assertThat(resumed.inalterados()).isEqualTo(3);
    assertThat(snapshot()).isEqualTo(committed); // Includes original confirmation timestamps.
    assertThat(service.validarPersistencia(service.planejar(), 1).aprovada()).isTrue();
  }

  private Photo photo(boolean regular, String visibility) {
    UUID fileId = UUID.randomUUID();
    UUID linkId = UUID.randomUUID();
    String key = expectedPreviewKey(fileId);
    jdbc.update("""
        insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,nome_original,
          mime_type,tamanho_bytes,largura,altura,sha256,status_arquivo,criado_em)
        values (?,'R2','privadas-preview-apply',?,'synthetic.jpg','image/jpeg',321,640,480,?,'VALIDADO',?)
        """, fileId, "hml/preview-apply/privadas/" + fileId + ".jpg", CHECKSUM, STAMP);
    int order = photos.size();
    jdbc.update("""
        insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,
          status,visibilidade_midia,criado_em,atualizado_em)
        values (?,?,?,'FOTO',?,?,'PUBLICAVEL',?,?,?)
        """, linkId, adId, fileId, order == 0 ? "CAPA" : "GALERIA", order, visibility, STAMP, STAMP);
    if (regular) {
      jdbc.update("""
          update arquivo_midia set preview_restrito_tipo='PREVIEW_RESTRITO',preview_restrito_chave=?,
            preview_restrito_pipeline_versao='v1',preview_restrito_status='DISPONIVEL',
            preview_restrito_confirmado_em=? where id=?
          """, key, STAMP.minusDays(2), fileId);
    }
    Photo photo = new Photo(fileId, linkId, key);
    photos.add(photo);
    return photo;
  }

  private void completeInventory() {
    INVENTORY_KEYS.set(photos.stream().map(Photo::previewKey).toList());
  }

  private void assertCompleteMetadata(Photo photo) {
    Map<String, Object> actual = jdbc.queryForMap("""
        select preview_restrito_tipo,preview_restrito_chave,preview_restrito_pipeline_versao,
          preview_restrito_status,preview_restrito_confirmado_em from arquivo_midia where id=?
        """, photo.fileId());
    assertThat(actual).containsEntry("preview_restrito_tipo", "PREVIEW_RESTRITO")
        .containsEntry("preview_restrito_chave", photo.previewKey())
        .containsEntry("preview_restrito_pipeline_versao", "v1")
        .containsEntry("preview_restrito_status", "DISPONIVEL");
    assertThat(actual.get("preview_restrito_confirmado_em")).isNotNull();
  }

  private Snapshot snapshot() {
    Map<UUID, String> files = new LinkedHashMap<>();
    Map<UUID, String> nonPreview = new LinkedHashMap<>();
    for (Photo photo : photos) {
      files.put(photo.fileId(), jdbc.queryForObject(
          "select to_jsonb(a)::text from arquivo_midia a where id=?", String.class, photo.fileId()));
      nonPreview.put(photo.fileId(), jdbc.queryForObject("""
          select (to_jsonb(a) - ARRAY['preview_restrito_tipo','preview_restrito_chave',
            'preview_restrito_pipeline_versao','preview_restrito_status','preview_restrito_confirmado_em'])::text
          from arquivo_midia a where id=?
          """, String.class, photo.fileId()));
    }
    List<String> links = jdbc.queryForList(
        "select to_jsonb(a)::text from anuncio_midia a where anuncio_id=? order by id", String.class, adId);
    String ad = jdbc.queryForObject("select to_jsonb(a)::text from anuncio a where id=?", String.class, adId);
    String user = jdbc.queryForObject("select to_jsonb(a)::text from usuario a where id=?", String.class, userId);
    return new Snapshot(Map.copyOf(files), Map.copyOf(nonPreview), List.copyOf(links), ad, user);
  }

  private void installSecondUpdateFault() {
    jdbc.execute("create schema " + FAULT_SCHEMA);
    jdbc.execute("create sequence " + FAULT_SCHEMA + ".attempts start 1");
    jdbc.execute("""
        create function %s.fail_second_update() returns trigger language plpgsql as $body$
        begin
          if OLD.preview_restrito_status='DESCONHECIDO' and NEW.preview_restrito_status='DISPONIVEL' then
            if nextval('%s.attempts')=2 then
              raise exception 'synthetic second preview batch failure';
            end if;
          end if;
          return NEW;
        end;
        $body$
        """.formatted(FAULT_SCHEMA, FAULT_SCHEMA));
    jdbc.execute("create trigger preview_apply_fault before update on arquivo_midia "
        + "for each row execute function " + FAULT_SCHEMA + ".fail_second_update()");
  }

  private void dropFault() {
    jdbc.execute("drop trigger if exists preview_apply_fault on arquivo_midia");
    jdbc.execute("drop schema if exists " + FAULT_SCHEMA + " cascade");
  }

  private static String expectedPreviewKey(UUID fileId) {
    try {
      String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest((fileId + ":" + CHECKSUM + ":v1").getBytes(StandardCharsets.UTF_8)));
      return PREVIEW_PREFIX + digest.substring(0, 32) + ".jpg";
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static Map<String, Object> properties() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("spring.datasource.url", jdbcUrl());
    result.put("spring.datasource.username", "topsv3test");
    result.put("spring.datasource.password", CREDENTIAL);
    result.put("spring.flyway.enabled", "false");
    result.put("spring.jpa.hibernate.ddl-auto", "validate");
    result.put("spring.jpa.open-in-view", "false");
    result.put("spring.task.scheduling.enabled", "false");
    result.put("app.storage.r2.enabled", "false");
    result.put("app.storage.r2.public-media-prefix", PUBLIC_PREFIX);
    result.put("app.storage.r2.private-media-prefix", "hml/preview-apply/privadas/");
    result.put("app.storage.r2.public-base-url", "https://preview-apply.invalid");
    result.put("app.outbox.email.enabled", "false");
    result.put("efi.pix.enabled", "false");
    result.put("efi.pix.reconciliation-enabled", "false");
    result.put("efi.pix.webhook-registration-enabled", "false");
    result.put("logging.level.root", "ERROR");
    return result;
  }

  private static String jdbcUrl() {
    return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_preview_apply";
  }

  private static void awaitPostgres() throws Exception {
    for (int attempt = 0; attempt < 60; attempt++) {
      if (commandExit(Map.of("PGPASSWORD", CREDENTIAL),
          "docker", "exec", "-e", "PGPASSWORD", CONTAINER,
          "pg_isready", "--host", "127.0.0.1", "--username", "topsv3test",
          "--dbname", "topsv3_preview_apply") == 0) return;
      Thread.sleep(500L);
    }
    throw new IllegalStateException("Owned PostgreSQL 17 did not become ready");
  }

  private static String command(String... arguments) throws Exception {
    return command(Map.of(), arguments);
  }

  private static String command(Map<String, String> environment, String... arguments) throws Exception {
    return owned.command(environment, Duration.ofSeconds(120),
        java.util.Arrays.copyOfRange(arguments, 1, arguments.length));
  }

  private static int commandExit(Map<String, String> environment, String... arguments) throws Exception {
    return owned.invoke(environment, Duration.ofSeconds(5),
        java.util.Arrays.copyOfRange(arguments, 1, arguments.length)).exit();
  }

  static final class FirstFailure implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    void remember(Throwable original) { failure.compareAndSet(null, original); }
    @Override public void handleTestExecutionException(ExtensionContext ignored, Throwable original) throws Throwable {
      remember(original);
      throw original;
    }
    @Override public void handleBeforeEachMethodExecutionException(ExtensionContext ignored, Throwable original) throws Throwable {
      remember(original);
      throw original;
    }
    @Override public void handleAfterEachMethodExecutionException(ExtensionContext ignored, Throwable original) throws Throwable {
      remember(original);
      throw original;
    }
  }

  private record Photo(UUID fileId, UUID linkId, String previewKey) { }
  private record Snapshot(Map<UUID, String> files, Map<UUID, String> nonPreview,
      List<String> links, String ad, String user) { }
}
