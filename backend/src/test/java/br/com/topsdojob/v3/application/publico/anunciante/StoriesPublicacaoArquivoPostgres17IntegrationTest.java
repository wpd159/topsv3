package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.premium.AdminPremiumOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarRequest;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeStoryRegistroService;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryAtivacaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MinhaContaStoryDto;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthenticationService;
import br.com.topsdojob.v3.application.publico.auth.dto.PublicProfileUpdateRequestDto;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Real production chain and schema; only the external object store is replaced. */
@SpringBootTest(properties = {
    "app.env=homologacao", "app.event.hash-salt=hash-fixture",
    "app.age-gate.signing-value=age-gate-runtime-test-value", "app.outbox.email.enabled=false",
    "app.storage.r2.enabled=true", "app.storage.r2.endpoint=https://127.0.0.1:1",
    "app.storage.r2.access-key=EXEMPLO_NAO_REAL", "app.storage.r2.signing-value=EXEMPLO_NAO_REAL",
    "app.storage.r2.private-media-bucket=privadas", "app.storage.r2.public-media-bucket=publicas",
    "app.storage.r2.document-bucket=documentos", "app.storage.r2.document-prefix=hml/documentos/",
    "app.storage.r2.private-media-prefix=hml/midias-pendentes/",
    "app.storage.r2.public-media-prefix=hml/midias-aprovadas/", "efi.pix.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.task.scheduling.enabled=false", "spring.datasource.hikari.maximum-pool-size=8"
})
@EnabledIfEnvironmentVariable(named = "STORIES_PUBLICACAO_ARQUIVO_POSTGRES17_ENABLED", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestExecutionListeners(listeners = StoriesPublicacaoArquivoPostgres17IntegrationTest.ContextCapture.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class StoriesPublicacaoArquivoPostgres17IntegrationTest {
  private static final UltimaFotoPostgres17Fixture POSTGRES = UltimaFotoPostgres17Fixture.start();
  private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(930000000);
  private static TestContext testContext;

  @Autowired private MinhaContaStoriesPublicacaoService publicacao;
  @Autowired private MinhaContaStoriesDireitoService direitos;
  @Autowired private StoryEncerramentoService encerramento;
  @Autowired private AdminStoryConfiguracaoService configuracao;
  @Autowired private ArquivoPublicidadeStoryRegistroService arquivo;
  @Autowired private CreditoLedgerOperacaoService ledger;
  @Autowired private AdminAnuncioAtualizacaoService adUpdates;
  @Autowired private AdminUsuarioAtualizacaoService userUpdates;
  @Autowired private PublicAuthenticationService profileUpdates;
  @Autowired private AdminPremiumOperacaoService premium;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockBean private ObjectStorage storage;
  @MockBean private ObjectStorageInventory inventory;
  @MockBean private R2VerificacaoAgrupadaPreviews remotePreviews;
  private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
  private final Set<String> deleted = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean failArchiveConfirmation = new AtomicBoolean();
  private final AtomicInteger archiveWrites = new AtomicInteger();

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::username);
    registry.add("spring.datasource.password", POSTGRES::credential);
  }

  public static final class ContextCapture extends AbstractTestExecutionListener {
    @Override public void beforeTestClass(TestContext context) { testContext = context; }
  }

  @AfterAll
  static void closeContextBeforeDatabase() throws Exception {
    if (testContext != null) testContext.markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
    POSTGRES.close();
  }

  @BeforeEach
  void syntheticStorage() {
    objects.clear();
    deleted.clear();
    failArchiveConfirmation.set(false);
    archiveWrites.set(0);
    when(storage.putIfAbsent(any(), anyString(), any(), anyString())).thenAnswer(call -> {
      String key = call.getArgument(1);
      if (key.contains("arquivo-publicidade/stories/")) archiveWrites.incrementAndGet();
      StoredObject value = new StoredObject(call.getArgument(2), call.getArgument(3));
      return objects.putIfAbsent(objectKey(call.getArgument(0), key), value) == null
          ? ObjectWriteResult.CREATED : ObjectWriteResult.ALREADY_EXISTS;
    });
    when(storage.exists(any(), anyString())).thenAnswer(call ->
        objects.containsKey(objectKey(call.getArgument(0), call.getArgument(1))));
    when(storage.get(any(), anyString())).thenAnswer(call -> {
      String key = call.getArgument(1);
      if (failArchiveConfirmation.get() && key.contains("arquivo-publicidade/stories/")) {
        throw new IllegalStateException("falha sintetica na confirmacao da copia privada");
      }
      return objects.get(objectKey(call.getArgument(0), key));
    });
    doAnswer(call -> {
      String key = objectKey(call.getArgument(0), call.getArgument(1));
      deleted.add(key);
      objects.remove(key);
      return null;
    }).when(storage).delete(any(), anyString());
    when(storage.publicUrl(any(), anyString())).thenReturn(
        Optional.of(URI.create("https://media.example.invalid/story-fixture")));
    when(storage.temporaryGetUrl(any(), anyString(), any())).thenReturn(
        URI.create("https://media.example.invalid/story-fixture"));
    when(remotePreviews.verificar(any())).thenReturn(Set.of());
  }

  @Test @Order(1)
  void adminCriaCatalogoAusentePublicaEArquivaNaMesmaTransacao() throws Exception {
    // This same assertion path must fail against the former MIDIA-only archive guard.
    assertThat(count("select count(*) from beneficio_premium where codigo='STORIES'")).isZero();
    Ad ad = ad();
    photo(ad);
    MinhaContaStoryDto result = publicacao.publicarAdministrativamente(
        ad.id(), "admin-catalogo-ausente", admin(), "story-catalogo-ausente");

    assertThat(value("select escopo from beneficio_premium where codigo='STORIES'"))
        .isEqualTo("ANUNCIO");
    assertArchived(result, "ADMINISTRATIVA");
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", ad.owner())).isZero();
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='STORY_PUBLICADO_ADMINISTRATIVAMENTE'",
        result.storyId())).isEqualTo(1);
  }

  @Test @Order(2)
  void catalogoRealExistenteAdminRepeteSemNovoDireitoCopiaOuDebito() throws Exception {
    UUID benefit = transaction().execute(status -> configuracao.garantirIdentidadeTecnica(now()).getId());
    Ad ad = ad();
    photo(ad);
    AdminUserPrincipal actor = admin();
    MinhaContaStoryDto first = publicacao.publicarAdministrativamente(ad.id(), "admin-existente", actor, "story-first");
    MinhaContaStoryDto repeated = publicacao.publicarAdministrativamente(ad.id(), "admin-existente", actor, "story-repeat");
    MinhaContaStoryDto active = publicacao.publicarAdministrativamente(ad.id(), "admin-other-key", actor, "story-active");

    assertThat(repeated.storyId()).isEqualTo(first.storyId());
    assertThat(active.storyId()).isEqualTo(first.storyId());
    assertThat(count("select count(*) from story_anuncio where anuncio_id=?", ad.id())).isEqualTo(1);
    assertThat(count("select count(*) from ativacao_beneficio where anuncio_id=? and beneficio_id=?", ad.id(), benefit)).isEqualTo(1);
    assertThat(count("select count(*) from grupo_ativacao_beneficio where anuncio_id=?", ad.id())).isEqualTo(1);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", ad.owner())).isZero();
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='STORY_PUBLICADO_ADMINISTRATIVAMENTE'",
        first.storyId())).isEqualTo(1);
    assertThat(archiveWrites).hasValue(1);
    assertArchived(first, "ADMINISTRATIVA");

    // A later content version reuses the verified private bytes, not another copy.
    transaction().executeWithoutResult(status -> {
      jdbc.update("update anuncio set titulo='Titulo sintetico atualizado' where id=?", ad.id());
      arquivo.registrarEstado(first.storyId(), "EDICAO_SINTETICA", "story-reuse", now());
    });
    assertThat(archiveWrites).hasValue(1);
    assertThat(count("""
        select count(*) from arquivo_publicidade_story_versao v
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id where j.story_id=?
        """, first.storyId())).isEqualTo(2);
    assertThat(count("""
        select count(*) from arquivo_publicidade_story_midia_referencia r
        join arquivo_publicidade_story_versao v on v.id=r.versao_id
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id where j.story_id=?
        """, first.storyId())).isEqualTo(1);
  }

  @Test @Order(3)
  void uploadIndependenteUsaCatalogoRealDebitaUmaVezEArquivaMidiaProcessada() throws Exception {
    UUID owner = user();
    UUID activation = paidRight(owner);
    MockMultipartFile file = upload();
    MinhaContaStoryDto first = publicacao.publicar("MIDIA_UPLOAD", null, List.of(file),
        "upload-canonico", auth(owner), "story-upload-first");
    MinhaContaStoryDto repeated = publicacao.publicar("MIDIA_UPLOAD", null, List.of(file),
        "upload-canonico", auth(owner), "story-upload-repeat");

    assertThat(first.anuncioId()).isNull();
    assertThat(first.modoConteudo()).isEqualTo("MIDIA_UPLOAD");
    assertThat(repeated.storyId()).isEqualTo(first.storyId());
    assertThat(ledger.consultarSaldo(owner)).isEqualTo(7);
    assertThat(count("select count(*) from movimento_credito where usuario_id=? and direcao='DEBITO'", owner)).isEqualTo(1);
    assertThat(count("select count(*) from story_anuncio where ativacao_beneficio_id=?", activation)).isEqualTo(1);
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("ATIVA");
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='STORY_PUBLICADO'", first.storyId())).isEqualTo(1);
    assertThat(archiveWrites).hasValue(1);
    assertArchived(first, "ORIGEM_INDETERMINADA");
  }

  @Test @Order(4)
  void falhaDepoisDeGravarCopiaReverteStoryArquivoVigenciaELimpaAmbosObjetos() throws Exception {
    UUID owner = user();
    UUID activation = paidRight(owner);
    MockMultipartFile file = upload();
    long filesBefore = count("select count(*) from arquivo_midia");
    failArchiveConfirmation.set(true);

    assertThatThrownBy(() -> publicacao.publicar("MIDIA_UPLOAD", null, List.of(file),
        "upload-rollback", auth(owner), "story-upload-rollback"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("falha sintetica na confirmacao da copia privada");

    assertThat(archiveWrites).hasValue(1);
    assertThat(objects).isEmpty();
    assertThat(deleted).hasSize(2);
    assertThat(count("select count(*) from arquivo_midia")).isEqualTo(filesBefore);
    assertThat(count("select count(*) from story_anuncio where criado_por=?", owner)).isZero();
    assertThat(count("select count(*) from arquivo_publicidade_story_veiculacao where contratante_usuario_id=?", owner)).isZero();
    assertThat(count("select count(*) from auditoria_evento where ator_usuario_id=? and acao='STORY_PUBLICADO'", owner)).isZero();
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("AGUARDANDO_MODERACAO");
    assertThat(jdbc.queryForObject("select inicio_em is null and fim_em is null from ativacao_beneficio where id=?",
        Boolean.class, activation)).isTrue();
    assertThat(ledger.consultarSaldo(owner)).isEqualTo(7);
    assertThat(count("select count(*) from movimento_credito where usuario_id=? and direcao='DEBITO'", owner)).isEqualTo(1);

    failArchiveConfirmation.set(false);
    MinhaContaStoryDto retry = publicacao.publicar("MIDIA_UPLOAD", null, List.of(file),
        "upload-rollback", auth(owner), "story-upload-retry");
    assertArchived(retry, "ORIGEM_INDETERMINADA");
    assertThat(ledger.consultarSaldo(owner)).isEqualTo(7);
  }

  @Test @Order(5)
  void outroTitularNaoConsomeDireitoNemPublicaAnuncio() throws Exception {
    Ad ad = ad();
    photo(ad);
    UUID other = user();
    assertThatThrownBy(() -> publicacao.publicar("ANUNCIO", ad.id(), null,
        "outro-titular", auth(other), "story-wrong-owner"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode().value()).isEqualTo(403));
    assertThat(count("select count(*) from story_anuncio where anuncio_id=?", ad.id())).isZero();
    assertThat(count("select count(*) from ativacao_beneficio where usuario_id=?", other)).isZero();
    assertThat(archiveWrites).hasValue(0);
  }

  @Test @Order(6)
  void arquivoRecusaCodigoEscopoEGrupoAusenteOuComTitularAnuncioOrigemDivergentes() throws Exception {
    Ad ad = ad();
    Ad other = ad();
    photo(ad);
    MinhaContaStoryDto story = publicacao.publicarAdministrativamente(ad.id(), "guard-direito", admin(), "story-guard");
    Map<String, Object> right = jdbc.queryForMap("""
        select ab.id,ab.beneficio_id,ab.grupo_ativacao_id from ativacao_beneficio ab
        join story_anuncio s on s.ativacao_beneficio_id=ab.id where s.id=?
        """, story.storyId());
    List<Runnable> incompatible = List.of(
        () -> jdbc.update("update beneficio_premium set codigo='OUTRO_BENEFICIO_SINTETICO' where id=?", right.get("beneficio_id")),
        () -> jdbc.update("update beneficio_premium set escopo='MIDIA' where id=?", right.get("beneficio_id")),
        () -> jdbc.update("update ativacao_beneficio set grupo_ativacao_id=null where id=?", right.get("id")),
        () -> jdbc.update("update grupo_ativacao_beneficio set usuario_id=? where id=?", other.owner(), right.get("grupo_ativacao_id")),
        () -> jdbc.update("update grupo_ativacao_beneficio set anuncio_id=? where id=?", other.id(), right.get("grupo_ativacao_id")),
        () -> jdbc.update("update grupo_ativacao_beneficio set origem='CREDITO' where id=?", right.get("grupo_ativacao_id")));

    for (Runnable mutation : incompatible) {
      assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
        mutation.run();
        arquivo.registrarEstado(story.storyId(), "GUARD_SINTETICO", "story-incompatible", now());
      })).isInstanceOf(IllegalStateException.class);
      assertThat(count("""
          select count(*) from arquivo_publicidade_story_versao v
          join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id where j.story_id=?
          """, story.storyId())).isEqualTo(1);
      assertThat(archiveWrites).hasValue(1);
    }
    assertArchived(story, "ADMINISTRATIVA");
  }

  @Test @Order(7)
  void uploadReaproveitaDireitoHistoricoPreservadoSemDesvincularAnuncioNemDebitarNovamente() throws Exception {
    Ad ad = ad();
    UUID activation = paidRight(ad.owner(), "ANUNCIO", ad.id());
    UUID legacyStory = legacyFailedUpload(ad, activation);

    var ended = encerramento.encerrarProprio(legacyStory, auth(ad.owner()), "story-legacy-failure");
    assertThat(ended.direitoPreservado()).isTrue();
    assertThat(ended.motivo()).isEqualTo("FALHA_TECNICA");
    assertThat(value("select status from ativacao_beneficio where id=?", activation))
        .isEqualTo("AGUARDANDO_MODERACAO");
    assertThat(count("select count(*) from arquivo_publicidade_story_veiculacao where story_id=?", legacyStory)).isZero();

    MinhaContaStoryDto replacement = publicacao.publicar("MIDIA_UPLOAD", null, List.of(upload()),
        "upload-direito-historico", auth(ad.owner()), "story-legacy-replacement");
    assertThat(replacement.anuncioId()).isNull();
    assertThat(jdbc.queryForObject("select ativacao_beneficio_id from story_anuncio where id=?",
        UUID.class, replacement.storyId())).isEqualTo(activation);
    assertThat(jdbc.queryForObject("""
        select ab.anuncio_id=? and gb.anuncio_id=? from ativacao_beneficio ab
        join grupo_ativacao_beneficio gb on gb.id=ab.grupo_ativacao_id where ab.id=?
        """, Boolean.class, ad.id(), ad.id(), activation)).isTrue();
    assertThat(count("select count(*) from story_anuncio where ativacao_beneficio_id=?", activation)).isEqualTo(2);
    assertThat(count("select count(*) from movimento_credito where usuario_id=? and direcao='DEBITO'", ad.owner())).isEqualTo(1);
    assertThat(ledger.consultarSaldo(ad.owner())).isEqualTo(7);
    assertThat(archiveWrites).hasValue(1);
    assertArchived(replacement, "ORIGEM_INDETERMINADA");
  }

  @Test @Order(8)
  void edicaoAdministrativaConfirmaDadosECapturaJuntosEReverteAmbosSeStorageFalha() throws Exception {
    ActiveStory active = activeStory("edicao-anuncio");
    locationCatalog();
    String previousTitle = value("select titulo from anuncio where id=?", active.ad().id());
    var request = new AdminAnuncioAtualizacaoRequest(
        "Titulo sintetico atualizado pelo admin", "Descricao sintetica atualizada pela operacao administrativa",
        "ACOMPANHANTE_FEMININA", new BigDecimal("250.00"), "GO", "Goiania", null,
        "Regiao central", List.of("MEU_LOCAL"), List.of("VIDEOCHAMADA"),
        value("select telefone_normalizado from usuario where id=?", active.ad().owner()), false);

    assertStorageRollback(active, () -> adUpdates.atualizar(active.ad().id(), request,
        active.actor(), "story-ad-edit-failure"));
    assertThat(value("select titulo from anuncio where id=?", active.ad().id())).isEqualTo(previousTitle);
    assertThat(count("select count(*) from anuncio_localizacao where anuncio_id=?", active.ad().id())).isZero();
    assertThat(count("select count(*) from documento_busca_anuncio where anuncio_id=?", active.ad().id())).isZero();
    assertThat(count("select count(*) from auditoria_evento where request_id='story-ad-edit-failure'")).isZero();

    adUpdates.atualizar(active.ad().id(), request, active.actor(), "story-ad-edit-success");
    assertThat(value("select titulo from anuncio where id=?", active.ad().id())).isEqualTo(request.titulo());
    assertThat(value("select status from anuncio where id=?", active.ad().id())).isEqualTo("PUBLICADO");
    assertThat(value("select status_moderacao from anuncio where id=?", active.ad().id())).isEqualTo("APROVADO");
    assertThat(value("select endereco_resumido from anuncio_localizacao where anuncio_id=?", active.ad().id()))
        .isEqualTo("Regiao central");
    assertThat(count("select count(*) from documento_busca_anuncio where anuncio_id=?", active.ad().id())).isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where request_id='story-ad-edit-success' and acao='ANUNCIO_EDICAO_ADMINISTRATIVA'"))
        .isEqualTo(1);
    assertThat(latestStory(active.story().storyId())).containsEntry("titulo", request.titulo())
        .containsEntry("cidade", "Goiania").containsEntry("numero", 2);
    assertThat(storyVersions(active.story().storyId())).hasSize(2);
  }

  @Test @Order(9)
  void telefoneAdministrativoSincronizaAnuncioECapturaAtomicosSemDuplicarVersao() throws Exception {
    ActiveStory active = activeStory("telefone-admin");
    String previousPhone = value("select telefone_normalizado from usuario where id=?", active.ad().owner());
    Integer previousVersion = jdbc.queryForObject("select versao from usuario where id=?", Integer.class, active.ad().owner());
    var request = new AdminUsuarioAtualizacaoRequestDto();
    request.setVersao(previousVersion);
    request.setTelefone("+5562" + PHONE_SEQUENCE.incrementAndGet());

    assertStorageRollback(active, () -> userUpdates.atualizar(active.ad().owner(), request,
        active.actor(), "story-phone-failure"));
    assertThat(value("select telefone_normalizado from usuario where id=?", active.ad().owner())).isEqualTo(previousPhone);
    assertThat(value("select whatsapp_normalizado from anuncio where id=?", active.ad().id())).isNull();
    assertThat(jdbc.queryForObject("select versao from usuario where id=?", Integer.class, active.ad().owner()))
        .isEqualTo(previousVersion);
    assertThat(count("select count(*) from auditoria_evento where request_id='story-phone-failure'")).isZero();

    userUpdates.atualizar(active.ad().owner(), request, active.actor(), "story-phone-success");
    assertThat(value("select telefone_normalizado from usuario where id=?", active.ad().owner())).isEqualTo(request.getTelefone());
    assertThat(value("select whatsapp_normalizado from anuncio where id=?", active.ad().id())).isEqualTo(request.getTelefone());
    assertThat(latestStory(active.story().storyId())).containsEntry("whatsapp", request.getTelefone()).containsEntry("numero", 2);
    assertThat(count("select count(*) from auditoria_evento where request_id='story-phone-success' and acao='USUARIO_DADOS_CADASTRAIS_ATUALIZAR'"))
        .isEqualTo(1);
    // The advertisement and owner fan-outs see identical content and must share one new version.
    assertThat(storyVersions(active.story().storyId())).hasSize(2);
  }

  @Test @Order(10)
  void perfilDoProprietarioAtualizaConteudoEContratanteOuReverteTudoSeArquivoFalha() throws Exception {
    ActiveStory active = activeStory("perfil-proprietario");
    String previousName = value("select nome from usuario where id=?", active.ad().owner());
    String previousPhone = value("select telefone_normalizado from usuario where id=?", active.ad().owner());
    var request = new PublicProfileUpdateRequestDto("Perfil sintetico " + active.ad().owner(),
        "+5562" + PHONE_SEQUENCE.incrementAndGet());

    assertStorageRollback(active, () -> profileUpdates.updateProfile(request, auth(active.ad().owner())));
    assertThat(value("select nome from usuario where id=?", active.ad().owner())).isEqualTo(previousName);
    assertThat(value("select telefone_normalizado from usuario where id=?", active.ad().owner())).isEqualTo(previousPhone);
    assertThat(value("select whatsapp_normalizado from anuncio where id=?", active.ad().id())).isNull();

    profileUpdates.updateProfile(request, auth(active.ad().owner()));
    assertThat(value("select nome from usuario where id=?", active.ad().owner())).isEqualTo(request.username());
    assertThat(value("select telefone_normalizado from usuario where id=?", active.ad().owner())).isEqualTo(request.telefone());
    assertThat(value("select whatsapp_normalizado from anuncio where id=?", active.ad().id())).isEqualTo(request.telefone());
    assertThat(latestStory(active.story().storyId())).containsEntry("nome_publico", request.username())
        .containsEntry("nome_contratante", request.username()).containsEntry("whatsapp", request.telefone())
        .containsEntry("numero", 2);
    assertThat(storyVersions(active.story().storyId())).hasSize(2);
  }

  @Test @Order(11)
  void premiumRealLiberaVideoECapturaStoryOuReverteDireitoAuditoriaEDoisArquivos() throws Exception {
    long fixtureStarted = System.nanoTime();
    ActiveStory active = activeStory("premium-video");
    video(active.ad());
    UUID benefit = jdbc.queryForObject("select id from beneficio_premium where codigo='VIDEO_1'", UUID.class);
    var request = new AdminPremiumAtivarRequest(benefit, 1, "Operacao sintetica de video");
    assertThat(latestStory(active.story().storyId())).containsEntry("midias", 1);
    long fixtureElapsed = System.nanoTime() - fixtureStarted;

    long rollbackStarted = System.nanoTime();
    assertStorageRollback(active, () -> premium.ativarManual(active.ad().id(), request,
        "story-premium-video", active.actor(), "story-premium-failure"));
    long rollbackElapsed = System.nanoTime() - rollbackStarted;
    assertThat(count("select count(*) from ativacao_beneficio where anuncio_id=? and beneficio_id=?", active.ad().id(), benefit)).isZero();
    assertThat(count("select count(*) from grupo_ativacao_beneficio where anuncio_id=?", active.ad().id())).isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where request_id='story-premium-failure'")).isZero();
    assertThat(count("select count(*) from arquivo_publicidade_veiculacao where anuncio_id=?", active.ad().id())).isZero();

    long[] storageBefore = storageCallCounts();
    long activationStarted = System.nanoTime();
    var granted = premium.ativarManual(active.ad().id(), request, "story-premium-video",
        active.actor(), "story-premium-success");
    // Freeze at the real proxied service return (including transaction completion), before diagnostics/assertions.
    long activationElapsed = System.nanoTime() - activationStarted;
    long[] storageAfter = storageCallCounts();
    System.out.printf(Locale.ROOT,
        "ADMIN_PREMIUM_TIMING_SYNTHETIC operation=ativarManual benefit=VIDEO_1 outcome=RETURNED "
            + "operationMs=%.3f fixtureSetupMs=%.3f priorRollbackProofMs=%.3f "
            + "storage=IN_MEMORY_NO_NETWORK getCalls=%d putIfAbsentCalls=%d deleteCalls=%d timingAssertion=false%n",
        activationElapsed / 1_000_000.0, fixtureElapsed / 1_000_000.0, rollbackElapsed / 1_000_000.0,
        storageAfter[0] - storageBefore[0], storageAfter[1] - storageBefore[1], storageAfter[2] - storageBefore[2]);
    var repeated = premium.ativarManual(active.ad().id(), request, "story-premium-video",
        active.actor(), "story-premium-repeat");
    assertThat(granted.status()).isEqualTo("ATIVA");
    assertThat(repeated.id()).isEqualTo(granted.id());
    assertThat(repeated.idempotente()).isTrue();
    assertThat(count("select count(*) from ativacao_beneficio where anuncio_id=? and beneficio_id=?", active.ad().id(), benefit)).isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='PREMIUM_ATIVACAO_ADMINISTRATIVA'", granted.id())).isEqualTo(1);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", active.ad().owner())).isZero();
    // The first ad fan-out captures both active ANUNCIO benefits: STORIES and VIDEO_1.
    assertThat(count("select count(*) from arquivo_publicidade_veiculacao where anuncio_id=?", active.ad().id())).isEqualTo(2);
    assertThat(count("select count(*) from arquivo_publicidade_veiculacao where ativacao_beneficio_id=?", granted.id())).isEqualTo(1);
    assertThat(count("""
        select count(*) from arquivo_publicidade_veiculacao j
        join story_anuncio s on s.ativacao_beneficio_id=j.ativacao_beneficio_id where s.id=?
        """, active.story().storyId())).isEqualTo(1);
    assertThat(latestStory(active.story().storyId())).containsEntry("midias", 2).containsEntry("numero", 2);
    assertThat(storyVersions(active.story().storyId())).hasSize(2);
    assertThat(count("""
        select count(*) from arquivo_publicidade_story_midia m
        join arquivo_publicidade_story_versao v on v.id=m.versao_id
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id where j.story_id=?
        """, active.story().storyId())).isEqualTo(2);
  }

  private long[] storageCallCounts() {
    var calls = org.mockito.Mockito.mockingDetails(storage).getInvocations();
    return new long[] {
        calls.stream().filter(call -> call.getMethod().getName().equals("get")).count(),
        calls.stream().filter(call -> call.getMethod().getName().equals("putIfAbsent")).count(),
        calls.stream().filter(call -> call.getMethod().getName().equals("delete")).count()
    };
  }

  @Test @Order(12)
  void uploadIndependenteRecusaVinculoDeAnuncioSemDireitoHistoricoPreservado() throws Exception {
    Ad ad = ad();
    UUID activation = paidRight(ad.owner());
    MinhaContaStoryDto story = publicacao.publicar("MIDIA_UPLOAD", null, List.of(upload()),
        "upload-sem-historico", auth(ad.owner()), "story-no-preserved-history");
    UUID group = jdbc.queryForObject("select grupo_ativacao_id from ativacao_beneficio where id=?",
        UUID.class, activation);
    List<Map<String, Object>> previousVersions = storyVersions(story.storyId());
    assertThat(story.anuncioId()).isNull();
    assertThat(count("""
        select count(*) from story_anuncio where ativacao_beneficio_id=?
          and direito_preservado=true and encerrado_em is not null
        """, activation)).isZero();

    assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
      // Both links and their holder agree; only the required preservation evidence is absent.
      jdbc.update("update ativacao_beneficio set anuncio_id=? where id=?", ad.id(), activation);
      jdbc.update("update grupo_ativacao_beneficio set anuncio_id=? where id=?", ad.id(), group);
      arquivo.registrarEstado(story.storyId(), "GUARD_SINTETICO", "story-no-history-guard", now());
    })).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("direito STORIES canonico");

    assertThat(jdbc.queryForObject("""
        select ab.anuncio_id is null and gb.anuncio_id is null from ativacao_beneficio ab
        join grupo_ativacao_beneficio gb on gb.id=ab.grupo_ativacao_id where ab.id=?
        """, Boolean.class, activation)).isTrue();
    assertThat(storyVersions(story.storyId())).isEqualTo(previousVersions);
    assertThat(archiveWrites).hasValue(1);
    assertArchived(story, "ORIGEM_INDETERMINADA");
  }

  private ActiveStory activeStory(String key) throws Exception {
    Ad ad = ad();
    photo(ad);
    AdminUserPrincipal actor = admin();
    MinhaContaStoryDto story = publicacao.publicarAdministrativamente(ad.id(), key, actor, "story-active-" + key);
    assertArchived(story, "ADMINISTRATIVA");
    return new ActiveStory(ad, story, actor);
  }

  private void assertStorageRollback(ActiveStory active, Runnable operation) {
    Set<String> previousObjects = Set.copyOf(objects.keySet());
    List<Map<String, Object>> previousVersions = storyVersions(active.story().storyId());
    int previousWrites = archiveWrites.get();
    failArchiveConfirmation.set(true);
    try {
      assertThatThrownBy(operation::run).isInstanceOf(IllegalStateException.class)
          .hasMessage("falha sintetica na confirmacao da copia privada");
    } finally {
      failArchiveConfirmation.set(false);
    }
    assertThat(archiveWrites.get()).isGreaterThan(previousWrites);
    assertThat(objects.keySet()).containsExactlyInAnyOrderElementsOf(previousObjects);
    assertThat(deleted).isNotEmpty().doesNotContainAnyElementsOf(previousObjects);
    assertThat(storyVersions(active.story().storyId())).isEqualTo(previousVersions);
  }

  private List<Map<String, Object>> storyVersions(UUID story) {
    return jdbc.queryForList("""
        select v.id,v.numero,v.vigente_desde,v.vigente_ate,v.conteudo_sha256,
          v.conteudo_json::text,v.contratante_json::text,v.comercial_json::text,j.fim_em
        from arquivo_publicidade_story_versao v
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id
        where j.story_id=? order by v.numero
        """, story);
  }

  private Map<String, Object> latestStory(UUID story) {
    return jdbc.queryForMap("""
        select v.numero,v.conteudo_json->>'titulo' as titulo,
          v.conteudo_json->>'whatsapp_normalizado' as whatsapp,
          v.conteudo_json->>'nomePublico' as nome_publico,
          v.contratante_json->>'nome' as nome_contratante,
          v.conteudo_json->'localizacao'->>'cidade' as cidade,
          jsonb_array_length(v.conteudo_json->'midias') as midias
        from arquivo_publicidade_story_versao v
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id
        where j.story_id=? order by v.numero desc limit 1
        """, story);
  }

  private void locationCatalog() {
    jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values(?,'GO','Goias','goias',?) on conflict(uf) do nothing",
        UUID.randomUUID(), now());
    UUID state = jdbc.queryForObject("select id from estado where uf='GO'", UUID.class);
    jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values(?,?,'Goiania','goiania','goiania',?) on conflict(estado_id,slug) do nothing",
        UUID.randomUUID(), state, now());
  }

  private void video(Ad ad) throws Exception {
    UUID file = UUID.randomUUID();
    String key = "hml/midias-pendentes/story-premium-video-" + file + ".mp4";
    // Preapproved media is a fixture input; this test exercises selection and byte preservation, not a codec.
    byte[] bytes = ("video-sintetico-preaprovado-" + file).getBytes(StandardCharsets.UTF_8);
    objects.put(objectKey(StorageArea.PRIVATE_MEDIA, key), new StoredObject(bytes, "video/mp4"));
    jdbc.update("""
        insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
          sha256,status_arquivo,criado_em)
        values(?,'R2','privadas',?,'video/mp4',?,?,'VALIDADO',?)
        """, file, key, bytes.length, sha256(bytes), now());
    jdbc.update("""
        insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
          visibilidade_midia,criado_em,atualizado_em)
        values(?,?,?,'VIDEO','GALERIA',1,'PUBLICAVEL','RESTRITA_18',?,?)
        """, UUID.randomUUID(), ad.id(), file, now(), now());
  }

  private UUID paidRight(UUID owner) {
    return paidRight(owner, "MIDIA_UPLOAD", null);
  }

  private UUID paidRight(UUID owner, String mode, UUID adId) {
    AdminUserPrincipal actor = admin();
    var before = configuracao.consultar();
    var configured = configuracao.salvar(new AdminStoryConfiguracaoRequest(true, 3, before.versao()),
        actor, "story-test-config");
    transaction().executeWithoutResult(status -> ledger.registrar(owner, TipoMovimentoCredito.ENTRADA,
        DirecaoMovimentoCredito.CREDITO, 10, 0, OrigemMovimentoCredito.AJUSTE_ADMIN,
        "FIXTURE_STORY", owner, "story-fixture-funding:" + owner, actor.usuarioId(),
        "Saldo sintetico para prova de Story", "story-fixture-funding"));
    var request = new MinhaContaStoryAtivacaoRequest(mode, adId, 3, configured.versao());
    var first = direitos.ativar(request, "paid-right", auth(owner), "story-right-first");
    var repeated = direitos.ativar(request, "paid-right", auth(owner), "story-right-repeat");
    assertThat(repeated.ativacaoId()).isEqualTo(first.ativacaoId());
    assertThat(repeated.idempotente()).isTrue();
    assertThat(first.saldoAtual()).isEqualTo(7);
    return first.ativacaoId();
  }

  private UUID legacyFailedUpload(Ad ad, UUID activation) throws Exception {
    // The former linked-upload shape has no current creation endpoint; seed only that historical row.
    // Rights/catalog, proven technical closure, restoration, replacement and archive all remain real.
    UUID story = UUID.randomUUID();
    UUID file = UUID.randomUUID();
    UUID link = UUID.randomUUID();
    byte[] bytes = png();
    String digest = sha256(bytes);
    String key = "hml/midias-pendentes/stories/historico/" + file + ".png";
    OffsetDateTime beginning = now().minusHours(1);
    objects.put(objectKey(StorageArea.PRIVATE_MEDIA, key), new StoredObject(bytes, "image/png"));
    transaction().executeWithoutResult(status -> {
      jdbc.update("""
          insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
            sha256,status_arquivo,criado_em)
          values(?,'R2','privadas',?,'image/png',?,?,'REJEITADO',?)
          """, file, key, bytes.length, digest, beginning);
      jdbc.update("""
          insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
            visibilidade_midia,criado_em,atualizado_em)
          values(?,?,?,'STORY','STORY',0,'PUBLICAVEL','RESTRITA_18',?,?)
          """, link, ad.id(), file, beginning, beginning);
      jdbc.update("update ativacao_beneficio set status='ATIVA',inicio_em=?,fim_em=? where id=?",
          beginning, beginning.plusHours(24), activation);
      jdbc.update("""
          insert into story_anuncio(id,anuncio_id,anuncio_midia_id,modo_conteudo,ativacao_beneficio_id,
            idempotency_key,request_fingerprint,status,inicio_em,fim_em,ordem,criado_por,criado_em,atualizado_em)
          values(?,?,?,'MIDIA_UPLOAD',?,'upload-historico',?,'PUBLICADO',?,?,0,?,?,?)
          """, story, ad.id(), link, activation, digest, beginning, beginning.plusHours(24),
          ad.owner(), beginning, beginning);
    });
    return story;
  }

  private void assertArchived(MinhaContaStoryDto story, String classification) throws Exception {
    assertThat(story.status()).isEqualTo("PUBLICADO");
    assertThat(story.fimEm()).isEqualTo(story.inicioEm().plusHours(24));
    assertThat(count("select count(*) from arquivo_publicidade_story_veiculacao where story_id=?", story.storyId())).isEqualTo(1);
    assertThat(value("select classificacao from arquivo_publicidade_story_veiculacao where story_id=?", story.storyId()))
        .isEqualTo(classification);
    List<Map<String, Object>> copies = jdbc.queryForList("""
        select m.chave_privada,m.sha256,m.tamanho_bytes,v.comercial_json->>'beneficioCodigo' as codigo,
          v.comercial_json->>'beneficioEscopo' as escopo
        from arquivo_publicidade_story_midia m join arquivo_publicidade_story_versao v on v.id=m.versao_id
        join arquivo_publicidade_story_veiculacao j on j.id=v.veiculacao_id where j.story_id=?
        """, story.storyId());
    assertThat(copies).hasSize(1);
    Map<String, Object> copy = copies.get(0);
    assertThat(copy.get("codigo")).isEqualTo("STORIES");
    assertThat(copy.get("escopo")).isEqualTo("ANUNCIO");
    StoredObject stored = objects.get(objectKey(StorageArea.PRIVATE_MEDIA, (String) copy.get("chave_privada")));
    assertThat(stored).isNotNull();
    assertThat(sha256(stored.content())).isEqualTo(copy.get("sha256"));
    assertThat(stored.content().length).isEqualTo(((Number) copy.get("tamanho_bytes")).intValue());
  }

  private Ad ad() {
    UUID owner = user();
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,
          criado_em,atualizado_em,publicado_em,ultima_publicacao_em,versao)
        values(?,?,?,'Story integrado sintetico','Descricao sintetica de teste',
          'PUBLICADO','APROVADO','ACOMPANHANTE_FEMININA',?,?,?,?,0)
        """, id, owner, "story-integrado-" + id, now(), now(), now(), now());
    return new Ad(id, owner);
  }

  private void photo(Ad ad) throws Exception {
    UUID file = UUID.randomUUID();
    UUID link = UUID.randomUUID();
    String key = "hml/midias-aprovadas/story-fixture-" + file + ".png";
    byte[] bytes = png();
    objects.put(objectKey(StorageArea.PUBLIC_MEDIA, key), new StoredObject(bytes, "image/png"));
    jdbc.update("""
        insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
          sha256,status_arquivo,criado_em)
        values(?,'R2','publicas',?,'image/png',?,?,'VALIDADO',?)
        """, file, key, bytes.length, sha256(bytes), now());
    jdbc.update("""
        insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
          visibilidade_midia,criado_em,atualizado_em)
        values(?,?,?,'FOTO','GALERIA',0,'PUBLICAVEL','LIVRE',?,?)
        """, link, ad.id(), file, now(), now());
  }

  private UUID user() {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into usuario(id,nome,email_normalizado,telefone_normalizado,status,tipo_conta,
          criado_em,atualizado_em,versao)
        values(?,?,?,?,'ATIVO','ANUNCIANTE',?,?,0)
        """, id, "story-fixture-" + id, id + "@example.invalid", "+5562" + PHONE_SEQUENCE.incrementAndGet(), now(), now());
    return id;
  }

  private AdminUserPrincipal admin() {
    UUID id = user();
    jdbc.update("update usuario set tipo_conta='STAFF' where id=?", id);
    return new AdminUserPrincipal(id, "Admin sintetico", id + "@example.invalid", null,
        List.of(PapelUsuario.ADMIN), List.of(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);
  }

  private Authentication auth(UUID owner) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new PublicUserPrincipal(owner, "story-fixture", owner + "@example.invalid"), null, List.of());
  }

  private MockMultipartFile upload() throws Exception {
    return new MockMultipartFile("arquivo", "story-sintetico.png", "image/png", png());
  }

  private byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(320, 480, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.dispose();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, "png", bytes)).isTrue();
    return bytes.toByteArray();
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private long count(String sql, Object... parameters) { return jdbc.queryForObject(sql, Long.class, parameters); }
  private String value(String sql, Object... parameters) { return jdbc.queryForObject(sql, String.class, parameters); }
  private TransactionTemplate transaction() { return new TransactionTemplate(transactionManager); }
  private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
  private static String objectKey(StorageArea area, String key) { return area + ":" + key; }
  private record Ad(UUID id, UUID owner) { }
  private record ActiveStory(Ad ad, MinhaContaStoryDto story, AdminUserPrincipal actor) { }
}
