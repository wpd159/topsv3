package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecisaoModeracaoAcao;
import br.com.topsdojob.v3.application.admin.premium.AdminPremiumOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumBeneficioStatusCalculado;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivarRequest;
import br.com.topsdojob.v3.application.admin.creditos.AdminCreditoOperacaoService;
import br.com.topsdojob.v3.application.admin.creditos.dto.AdminCreditoAjusteRequest;
import br.com.topsdojob.v3.application.arquivo.ArquivoPublicidadeRegistroService;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.application.publico.premium.MinhaContaPremiumService;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumRequest;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumItemRequest;
import br.com.topsdojob.v3.domain.shared.VisibilidadeMidia;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.AtivacaoBeneficioRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real services, repositories and PostgreSQL locks; only external storage is replaced. */
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
@EnabledIfEnvironmentVariable(named = "ANUNCIANTE_CONCURRENCY_POSTGRES17_ENABLED", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = FotosExtrasCancelamentoPostgres17IntegrationTest.ContextCapture.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class FotosExtrasCancelamentoPostgres17IntegrationTest {
  private static final UltimaFotoPostgres17Fixture POSTGRES = UltimaFotoPostgres17Fixture.start();
  private static TestContext testContext;

  @Autowired private AdminPremiumOperacaoService premium;
  @Autowired private BeneficioAnuncioConsultaService beneficios;
  @Autowired private AdminCreditoOperacaoService creditos;
  @Autowired private CreditoLedgerOperacaoService ledger;
  @Autowired private MinhaContaPremiumService compras;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private AdminModeracaoAcaoService moderacao;
  @Autowired private ArquivoPublicidadeRegistroService arquivo;
  @Autowired private AnuncioRepository anuncios;
  @Autowired private AtivacaoBeneficioRepository ativacoes;
  @Autowired private UsuarioRepository usuarios;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private ObjectStorage storage;
  @MockBean private ObjectStorageInventory inventory;
  @MockBean private R2VerificacaoAgrupadaPreviews remotePreviews;
  private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<String> lockOrder = new ConcurrentLinkedQueue<>();
  private final ThreadLocal<String> operation = new ThreadLocal<>();

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
    lockOrder.clear();
    when(storage.putIfAbsent(any(), anyString(), any(), anyString())).thenAnswer(call -> {
      StoredObject value = new StoredObject(call.getArgument(2), call.getArgument(3));
      return objects.putIfAbsent(objectKey(call.getArgument(0), call.getArgument(1)), value) == null
          ? ObjectWriteResult.CREATED : ObjectWriteResult.ALREADY_EXISTS;
    });
    when(storage.get(any(), anyString())).thenAnswer(call ->
        objects.get(objectKey(call.getArgument(0), call.getArgument(1))));
    when(storage.exists(any(), anyString())).thenAnswer(call ->
        objects.containsKey(objectKey(call.getArgument(0), call.getArgument(1))));
    doAnswer(call -> {
      objects.remove(objectKey(call.getArgument(0), call.getArgument(1)));
      return null;
    }).when(storage).delete(any(), anyString());
    when(storage.publicUrl(any(), anyString())).thenAnswer(call ->
        Optional.of(URI.create("https://media.example.invalid/" + call.getArgument(1))));
    when(storage.temporaryGetUrl(any(), anyString(), any())).thenAnswer(call ->
        URI.create("https://media.example.invalid/" + call.getArgument(1)));
    when(remotePreviews.verificar(any())).thenReturn(Set.of());
  }

  @Test
  void cancelarAntesDoUsoPreservaDatasNulasSemCreditoERepeticaoIdempotente() throws Exception {
    Fixture fixture = seed();
    AdminUserPrincipal admin = admin();
    UUID activation = premium.ativarManual(fixture.ad(),
        new AdminPremiumAtivarRequest(fixture.benefit(), 7, "Cancelamento serial antes do uso"),
        "fotos-extra-serial-ativar-" + fixture.ad(), admin, "fotos-extra-serial-ativar").id();
    assertThat(value("select status from ativacao_beneficio where id=?", activation))
        .isEqualTo("AGUARDANDO_MODERACAO");
    assertThat(count("select count(*) from ativacao_beneficio where id=? and inicio_em is null and fim_em is null", activation))
        .isEqualTo(1);
    Map<String, Object> groupBefore = jdbc.queryForMap("""
        select g.* from grupo_ativacao_beneficio g join ativacao_beneficio a on a.grupo_ativacao_id=g.id
        where a.id=?
        """, activation);

    Outcome outcome = run("SERIAL_CANCELAMENTO", () -> premium.cancelar(activation,
        "Cortesia encerrada antes do uso", "fotos-extra-serial-cancelar", admin, "fotos-extra-serial-cancelar"));
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_SERIAL_STATE="
        + value("select status from ativacao_beneficio where id=?", activation)
        + " creditos=" + count("select count(*) from movimento_credito where usuario_id=?", fixture.owner()));
    assertThat(outcome.failure()).as("cancelamento serial SQLState=%s", outcome.sqlStates()).isNull();

    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("REVOGADA");
    assertThat(count("""
        select count(*) from ativacao_beneficio where id=? and inicio_em is null and fim_em is null
          and revogada_em is not null and motivo_revogacao='Cortesia encerrada antes do uso'
          and origem='ADMIN' and custo_creditos_snapshot=0
        """, activation)).isEqualTo(1);
    Map<String, Object> revoked = jdbc.queryForMap("select * from ativacao_beneficio where id=?", activation);
    var repeated = premium.cancelar(activation, "Tentativa repetida nao reescreve historico",
        "fotos-extra-serial-cancelar", admin, "fotos-extra-serial-retry");
    assertThat(repeated.idempotente()).isTrue();
    assertThat(repeated.creditosEstornados()).isZero();
    assertThat(jdbc.queryForMap("select * from ativacao_beneficio where id=?", activation)).isEqualTo(revoked);
    assertThat(jdbc.queryForMap("select * from grupo_ativacao_beneficio where id=?", groupBefore.get("id")))
        .isEqualTo(groupBefore);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", fixture.owner())).isZero();
    assertThat(count("select count(*) from arquivo_publicidade_veiculacao where ativacao_beneficio_id=?", activation)).isZero();
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='PREMIUM_ATIVACAO_CANCELAR'", activation))
        .isEqualTo(1);
    var calculated = beneficios.consultarCalculados(fixture.ad()).stream()
        .filter(item -> activation.equals(item.ativacao().getId())).findFirst().orElseThrow();
    assertThat(calculated.status()).isEqualTo(PremiumBeneficioStatusCalculado.INATIVO);
    assertThat(calculated.inconsistente()).isFalse();
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_SERIAL_RESULT=OK dates=null ledger=0 audit=1 idempotent=true policy=INATIVO");
  }

  @Test
  void constraintRejeitaCombinacoesInvalidasEPreservaRamosComJanela() throws Exception {
    Fixture fixture = seed();
    UUID activation = activate(fixture, admin());
    OffsetDateTime instant = now();
    List<WindowCase> invalid = List.of(
        new WindowCase("ATIVA", null, null, null, null),
        new WindowCase("AGENDADA", null, null, null, null),
        new WindowCase("EXPIRADA", null, null, null, null),
        new WindowCase("CANCELADA", null, null, instant, "Motivo sintetico"),
        new WindowCase("REVOGADA", null, null, null, "Motivo sintetico"),
        new WindowCase("REVOGADA", null, null, instant, null),
        new WindowCase("REVOGADA", null, null, instant, ""),
        new WindowCase("REVOGADA", null, null, instant, "   "),
        new WindowCase("REVOGADA", instant, null, instant, "Motivo sintetico"),
        new WindowCase("REVOGADA", null, instant, instant, "Motivo sintetico"),
        new WindowCase("REVOGADA", instant, instant, instant, "Motivo sintetico"),
        new WindowCase("REVOGADA", instant, instant.minusSeconds(1), instant, "Motivo sintetico"),
        new WindowCase("AGUARDANDO_MODERACAO", instant, instant.plusDays(7), null, null));
    for (WindowCase candidate : invalid) {
      Throwable failure = catchThrowable(() -> writeWindow(activation, candidate));
      assertThat(failure).as("combinação inválida %s", candidate).isNotNull();
      assertThat(sqlStates(failure)).as("constraint para %s", candidate).contains("23514");
      assertThat(value("select status from ativacao_beneficio where id=?", activation))
          .isEqualTo("AGUARDANDO_MODERACAO");
    }
    // The pre-existing non-null branch remains valid for every terminal/started status.
    for (String status : List.of("ATIVA", "AGENDADA", "EXPIRADA", "REVOGADA", "CANCELADA")) {
      assertThat(writeWindow(activation, new WindowCase(status, instant, instant.plusDays(7), null, null)))
          .isEqualTo(1);
    }
    assertThat(writeWindow(activation, new WindowCase("REVOGADA", null, null, instant, "Motivo sintetico")))
        .isEqualTo(1);
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_CONSTRAINT_RESULT=OK invalid=13 originalDatedBranches=5 revokedWithoutUse=true");
  }

  @Test
  void cancelarDepoisDaAprovacaoPreservaJanelaEfetivaEArquivo() throws Exception {
    Fixture fixture = seed();
    AdminUserPrincipal admin = admin();
    UUID activation = activate(fixture, admin);
    moderacao.decidirMidia(fixture.fifth(), new AdminDecidirMidiaRequestDto(
        fixture.ad(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE, null, null, null),
        admin, "fotos-extra-iniciar-serial");
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("ATIVA");
    Map<String, Object> dates = jdbc.queryForMap("select inicio_em,fim_em from ativacao_beneficio where id=?", activation);
    assertThat(dates.get("inicio_em")).isNotNull();
    assertThat(dates.get("fim_em")).isNotNull();
    premium.cancelar(activation, "Encerramento depois de inicio efetivo", "fotos-extra-iniciada-cancelar",
        admin, "fotos-extra-iniciada-cancelar");
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("REVOGADA");
    assertThat(jdbc.queryForMap("select inicio_em,fim_em from ativacao_beneficio where id=?", activation)).isEqualTo(dates);
    assertThat(count("""
        select count(*) from arquivo_publicidade_veiculacao where ativacao_beneficio_id=?
          and encerramento_motivo='PREMIUM_ATIVACAO_CANCELADA'
        """, activation)).isEqualTo(1);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", fixture.owner())).isZero();
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_STARTED_RESULT=OK effectiveDatesPreserved=true archiveClosed=1 ledger=0");
  }

  @Test
  void compraComCreditoEstornaUmaVezEReverteIntegralmenteNaFalha() throws Exception {
    Fixture fixture = seed();
    AdminUserPrincipal admin = admin();
    creditos.ajustar(fixture.owner(), new AdminCreditoAjusteRequest("CREDITO", 100, "Saldo sintetico do teste"),
        "fotos-extra-credito-inicial", admin, "fotos-extra-credito-inicial");
    var auth = UsernamePasswordAuthenticationToken.authenticated(
        new PublicUserPrincipal(fixture.owner(), "Pessoa sintetica", fixture.owner() + "@example.invalid"), null, List.of());
    var purchase = compras.comprar(new MinhaCompraPremiumRequest("fotos-extras-" + fixture.ad(),
        List.of(new MinhaCompraPremiumItemRequest("FOTOS_EXTRA_5", 7))),
        "fotos-extra-compra", auth, "fotos-extra-compra");
    assertThat(purchase.totalDebitado()).isPositive();
    UUID activation = jdbc.queryForObject("select id from ativacao_beneficio where anuncio_id=?", UUID.class, fixture.ad());
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("AGUARDANDO_MODERACAO");
    Map<String, Object> before = jdbc.queryForMap("select * from ativacao_beneficio where id=?", activation);
    long ledgerBefore = count("select count(*) from movimento_credito where usuario_id=?", fixture.owner());

    assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
      premium.cancelar(activation, "Cancelamento sintetico com rollback", "fotos-extra-compra-cancelar",
          admin, "fotos-extra-compra-rollback");
      throw new IllegalStateException("falha sintetica depois do flush");
    })).isInstanceOf(IllegalStateException.class).hasMessage("falha sintetica depois do flush");
    assertThat(jdbc.queryForMap("select * from ativacao_beneficio where id=?", activation)).isEqualTo(before);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", fixture.owner())).isEqualTo(ledgerBefore);
    assertThat(ledger.consultarSaldo(fixture.owner())).isEqualTo(100 - purchase.totalDebitado());
    assertThat(count("select count(*) from auditoria_evento where request_id='fotos-extra-compra-rollback'")).isZero();
    assertThat(count("select count(*) from arquivo_publicidade_veiculacao where ativacao_beneficio_id=?", activation)).isZero();

    var cancelled = premium.cancelar(activation, "Cancelamento sintetico depois da falha", "fotos-extra-compra-cancelar",
        admin, "fotos-extra-compra-cancelar");
    var repeated = premium.cancelar(activation, "Cancelamento sintetico repetido", "fotos-extra-compra-cancelar",
        admin, "fotos-extra-compra-retry");
    assertThat(cancelled.creditosEstornados()).isEqualTo(purchase.totalDebitado());
    assertThat(repeated.idempotente()).isTrue();
    assertThat(repeated.creditosEstornados()).isZero();
    assertThat(ledger.consultarSaldo(fixture.owner())).isEqualTo(100);
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", fixture.owner())).isEqualTo(ledgerBefore + 1);
    assertThat(count("select count(*) from movimento_credito where usuario_id=? and tipo='ESTORNO'", fixture.owner())).isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='PREMIUM_ATIVACAO_CANCELAR'", activation)).isEqualTo(1);
    assertThat(count("select count(*) from ativacao_beneficio where id=? and status='REVOGADA' and inicio_em is null and fim_em is null", activation)).isEqualTo(1);
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_CREDIT_RESULT=OK rollbackAtomic=true refund=1 retryRefund=0 balanceRestored=true dates=null");
  }

  private UUID activate(Fixture fixture, AdminUserPrincipal admin) {
    return premium.ativarManual(fixture.ad(), new AdminPremiumAtivarRequest(fixture.benefit(), 7, "Cortesia sintetica"),
        "fotos-extra-ativar-" + fixture.ad(), admin, "fotos-extra-ativar").id();
  }

  private int writeWindow(UUID activation, WindowCase candidate) {
    return jdbc.update("""
        update ativacao_beneficio set status=?,inicio_em=?,fim_em=?,revogada_em=?,motivo_revogacao=? where id=?
        """, candidate.status(), candidate.start(), candidate.end(), candidate.revoked(), candidate.reason(), activation);
  }

  @Test
  void cancelarFotosExtrasEAprovarQuintaFotoConcluemSemDeadlock() throws Exception {
    concorrenciaAprovacao(false);
  }

  @Test
  void cancelarComCreditoEAprovarQuintaFotoPreservaCompatibilidadeDaFkDoTitular() throws Exception {
    concorrenciaAprovacao(true);
  }

  private void concorrenciaAprovacao(boolean creditPurchase) throws Exception {
    assertThat(mockingDetails(AopTestUtils.getUltimateTargetObject(premium)).isMock()).isFalse();
    assertThat(mockingDetails(AopTestUtils.getUltimateTargetObject(moderacao)).isMock()).isFalse();
    assertThat(mockingDetails(AopTestUtils.getUltimateTargetObject(arquivo)).isMock()).isFalse();
    Fixture fixture = seed();
    AdminUserPrincipal admin = admin();
    UUID activation;
    if (creditPurchase) {
      creditos.ajustar(fixture.owner(), new AdminCreditoAjusteRequest("CREDITO", 100, "Saldo sintetico moderacao"),
          "credito-moderacao-inicial", admin, "credito-moderacao-inicial");
      var auth = UsernamePasswordAuthenticationToken.authenticated(
          new PublicUserPrincipal(fixture.owner(), "Pessoa sintetica", fixture.owner() + "@example.invalid"), null, List.of());
      compras.comprar(new MinhaCompraPremiumRequest("fotos-extras-" + fixture.ad(),
          List.of(new MinhaCompraPremiumItemRequest("FOTOS_EXTRA_5", 7))),
          "credito-moderacao-fotos", auth, "credito-moderacao-fotos");
      activation = jdbc.queryForObject("select id from ativacao_beneficio where anuncio_id=?", UUID.class, fixture.ad());
    } else {
      activation = activate(fixture, admin);
    }
    assertThat(value("select status from ativacao_beneficio where id=?", activation))
        .isEqualTo("AGUARDANDO_MODERACAO");
    assertThat(count("select count(*) from movimento_credito where usuario_id=?", fixture.owner()))
        .isEqualTo(creditPurchase ? 2 : 0);
    assertThat(count("select count(*) from anuncio_midia where anuncio_id=? and status='PUBLICAVEL'", fixture.ad()))
        .isEqualTo(4);
    assertThat(value("select status from anuncio_midia where id=?", fixture.fifth())).isEqualTo("PENDENTE");
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_INITIAL_STATE=anuncio:PUBLICADO fotosPublicaveis:4 quintaFoto:PENDENTE ativacao:AGUARDANDO_MODERACAO origem:"
        + (creditPurchase ? "CREDITO" : "ADMIN"));

    CountDownLatch adLockedByApproval = new CountDownLatch(1);
    CountDownLatch cancellationReachedLock = new CountDownLatch(1);
    AtomicBoolean approvalBarrierUsed = new AtomicBoolean();
    CountDownLatch cancellationOwnsUser = new CountDownLatch(1);
    CountDownLatch ownerSqlCaptured = new CountDownLatch(1);
    AtomicBoolean firstCancellationOwnerLock = new AtomicBoolean();
    AtomicInteger cancellationPid = new AtomicInteger();
    MethodInterceptor ownerBarrier = call -> {
      boolean capture = "CANCELAMENTO".equals(operation.get())
          && call.getMethod().getName().equals("findByIdForUpdate")
          && fixture.owner().equals(call.getArguments()[0])
          && firstCancellationOwnerLock.compareAndSet(false, true);
      if (capture) cancellationPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
      Object result = call.proceed();
      if (capture) {
        lockOrder.add("B:usuario:adquirido");
        cancellationOwnsUser.countDown();
        await(ownerSqlCaptured, "SQL do lock do titular capturado antes do anuncio");
      }
      return result;
    };

    // These observation spies never fabricate repository results: proceed()
    // executes the real Spring Data query and its PostgreSQL row lock.
    MethodInterceptor adBarrier = call -> {
      if (!call.getMethod().getName().equals("findByIdForModeration")
          || !fixture.ad().equals(call.getArguments()[0])) return call.proceed();
      String actor = operation.get();
      if ("CANCELAMENTO".equals(actor)) {
        lockOrder.add("B:anuncio:solicitado");
        // With ad-first locking, let A finish before B waits on that same ad.
        cancellationReachedLock.countDown();
      }
      Object result = call.proceed();
      if ("APROVACAO".equals(actor) && approvalBarrierUsed.compareAndSet(false, true)) {
        lockOrder.add("A:anuncio:adquirido");
        adLockedByApproval.countDown();
        await(cancellationReachedLock, "cancelamento alcancou primeiro lock");
      } else if ("CANCELAMENTO".equals(actor)) {
        lockOrder.add("B:anuncio:adquirido");
      }
      return result;
    };
    MethodInterceptor activationBarrier = call -> {
      String method = call.getMethod().getName();
      String actor = operation.get();
      if ("APROVACAO".equals(actor) && method.equals("findAguardandoModeracaoForUpdate")) {
        lockOrder.add("A:ativacao-pendente:solicitada");
      }
      Object result = call.proceed();
      if ("CANCELAMENTO".equals(actor) && method.equals("findByIdForUpdate")
          && activation.equals(call.getArguments()[0])) {
        lockOrder.add("B:ativacao:adquirida");
        // Before the fix B owns the activation, then its real archive call
        // requests A's ad while A requests B's activation: PostgreSQL 40P01.
        cancellationReachedLock.countDown();
      }
      return result;
    };
    Advised adProxy = (Advised) anuncios;
    Advised activationProxy = (Advised) ativacoes;
    Advised ownerProxy = (Advised) usuarios;
    adProxy.addAdvice(0, adBarrier);
    activationProxy.addAdvice(0, activationBarrier);
    ownerProxy.addAdvice(0, ownerBarrier);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Outcome> outcomes = new ArrayList<>();
    try {
      Future<Outcome> approval = executor.submit(() -> run("APROVACAO", () ->
          moderacao.decidirMidia(fixture.fifth(), new AdminDecidirMidiaRequestDto(
              fixture.ad(), AdminDecisaoModeracaoAcao.APROVAR, VisibilidadeMidia.LIVRE,
              null, null, null), admin, "fotos-extra-aprovar")));
      await(adLockedByApproval, "aprovacao adquiriu anuncio");
      Future<Outcome> cancellation = executor.submit(() -> run("CANCELAMENTO", () ->
          premium.cancelar(activation, "Cancelamento sintetico concorrente", "fotos-extra-cancelar",
              admin, "fotos-extra-cancelar")));
      await(cancellationOwnsUser, "cancelamento possui usuario enquanto aprovacao possui anuncio");
      String ownerSql = jdbc.queryForObject("select query from pg_stat_activity where pid=?", String.class, cancellationPid.get());
      System.out.println("FOTOS_EXTRAS_CANCELAMENTO_OWNER_LOCK_SQL=" + ownerSql.replaceAll("\\s+", " "));
      assertThat(ownerSql).containsIgnoringCase("for no key update");
      ownerSqlCaptured.countDown();
      outcomes.add(approval.get(30, TimeUnit.SECONDS));
      outcomes.add(cancellation.get(30, TimeUnit.SECONDS));
    } finally {
      cancellationReachedLock.countDown();
      ownerSqlCaptured.countDown();
      executor.shutdownNow();
      boolean stopped = executor.awaitTermination(10, TimeUnit.SECONDS);
      adProxy.removeAdvice(adBarrier);
      activationProxy.removeAdvice(activationBarrier);
      ownerProxy.removeAdvice(ownerBarrier);
      System.out.println("FOTOS_EXTRAS_CANCELAMENTO_LOCK_ORDER=" + lockOrder);
      System.out.println("FOTOS_EXTRAS_CANCELAMENTO_WORKERS_STOPPED=" + stopped);
      assertThat(stopped).as("workers encerrados antes do cleanup PostgreSQL").isTrue();
    }

    Map<String, Object> state = Map.of(
        "anuncio", value("select status from anuncio where id=?", fixture.ad()),
        "quintaFoto", value("select status from anuncio_midia where id=?", fixture.fifth()),
        "ativacao", value("select status from ativacao_beneficio where id=?", activation),
        "creditos", count("select count(*) from movimento_credito where usuario_id=?", fixture.owner()),
        "arquivo", count("select count(*) from arquivo_publicidade_veiculacao where ativacao_beneficio_id=?", activation));
    System.out.println("FOTOS_EXTRAS_CANCELAMENTO_FINAL_STATE=" + state);
    assertThat(outcomes).hasSize(2).allSatisfy(outcome ->
        assertThat(outcome.failure()).as("%s SQLState=%s locks=%s", outcome.operation(), outcome.sqlStates(), lockOrder).isNull());
    assertThat(state.get("anuncio")).isEqualTo("PUBLICADO");
    assertThat(state.get("quintaFoto")).isEqualTo("PUBLICAVEL");
    assertThat(state.get("ativacao")).isEqualTo("REVOGADA");
    assertThat(state.get("creditos")).isEqualTo(creditPurchase ? 3L : 0L);
    assertThat(lockOrder).containsSubsequence("A:anuncio:adquirido", "B:usuario:adquirido", "B:anuncio:solicitado");
    if (creditPurchase) {
      assertThat(ledger.consultarSaldo(fixture.owner())).isEqualTo(100);
      assertThat(count("select count(*) from movimento_credito where usuario_id=? and tipo='ESTORNO'", fixture.owner())).isEqualTo(1);
    }
    assertThat(state.get("arquivo")).isEqualTo(1L);
    assertThat(count("""
        select count(*) from arquivo_publicidade_veiculacao
        where ativacao_beneficio_id=? and fim_em is not null
          and encerramento_motivo='PREMIUM_ATIVACAO_CANCELADA'
        """, activation)).isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='PREMIUM_ATIVACAO_CANCELAR'", activation))
        .isEqualTo(1);
    assertThat(count("select count(*) from auditoria_evento where recurso_id=? and acao='PREMIUM_FOTOS_INICIAR_APOS_MODERACAO'", activation))
        .isEqualTo(1);
    System.out.println((creditPurchase ? "FOTOS_EXTRAS_CREDITO_MODERACAO_RESULT" : "FOTOS_EXTRAS_CANCELAMENTO_POSTGRES17_RESULT")
        + "=OK transactions=2 retries=0 refund=" + (creditPurchase ? 1 : 0) + " archive=real ownerFkCompatible=true");
  }

  @Test
  void cancelarComCreditoEComprarOutroBeneficioConcluemSemDeadlock() throws Exception {
    Fixture fixture = seed();
    AdminUserPrincipal admin = admin();
    creditos.ajustar(fixture.owner(), new AdminCreditoAjusteRequest("CREDITO", 100, "Saldo sintetico concorrente"),
        "credito-concorrente-inicial", admin, "credito-concorrente-inicial");
    var auth = UsernamePasswordAuthenticationToken.authenticated(
        new PublicUserPrincipal(fixture.owner(), "Pessoa sintetica", fixture.owner() + "@example.invalid"), null, List.of());
    compras.comprar(new MinhaCompraPremiumRequest("fotos-extras-" + fixture.ad(),
        List.of(new MinhaCompraPremiumItemRequest("FOTOS_EXTRA_5", 7))),
        "credito-concorrente-fotos", auth, "credito-concorrente-fotos");
    UUID activation = jdbc.queryForObject("select id from ativacao_beneficio where anuncio_id=?", UUID.class, fixture.ad());
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("AGUARDANDO_MODERACAO");

    CountDownLatch cancellationReachedFirstLock = new CountDownLatch(1);
    CountDownLatch purchaseOwnsUser = new CountDownLatch(1);
    CountDownLatch ownerSqlCaptured = new CountDownLatch(1);
    AtomicBoolean cancellationOwnsAd = new AtomicBoolean();
    AtomicBoolean firstPurchaseOwnerLock = new AtomicBoolean();
    AtomicInteger purchasePid = new AtomicInteger();
    AtomicInteger purchasedCost = new AtomicInteger();
    MethodInterceptor adBarrier = call -> {
      Object result = call.proceed();
      if ("CANCELAMENTO_CREDITO".equals(operation.get())
          && call.getMethod().getName().equals("findByIdForModeration")
          && fixture.ad().equals(call.getArguments()[0])) {
        lockOrder.add("A:anuncio:adquirido");
        cancellationOwnsAd.set(true);
        cancellationReachedFirstLock.countDown();
        await(purchaseOwnsUser, "compra adquiriu usuario real");
      }
      return result;
    };
    MethodInterceptor ownerBarrier = call -> {
      if (!call.getMethod().getName().equals("findByIdForUpdate")
          || !fixture.owner().equals(call.getArguments()[0])) return call.proceed();
      String actor = operation.get();
      if ("CANCELAMENTO_CREDITO".equals(actor)) {
        lockOrder.add("A:usuario:solicitado");
        if (!cancellationOwnsAd.get()) {
          // An owner-first implementation waits before owning any ad lock.
          cancellationReachedFirstLock.countDown();
          await(purchaseOwnsUser, "compra adquiriu usuario antes do cancelamento");
        }
      }
      boolean capture = "COMPRA_CONCORRENTE".equals(actor)
          && firstPurchaseOwnerLock.compareAndSet(false, true);
      if (capture) purchasePid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
      Object result = call.proceed();
      if (capture) {
        lockOrder.add("B:usuario:adquirido");
        purchaseOwnsUser.countDown();
        // The main connection observes the actual last SQL, not a guessed dialect lock.
        await(ownerSqlCaptured, "SQL real do owner capturado");
      } else if ("CANCELAMENTO_CREDITO".equals(actor)) {
        lockOrder.add("A:usuario:adquirido");
      }
      return result;
    };
    Advised adProxy = (Advised) anuncios;
    Advised ownerProxy = (Advised) usuarios;
    adProxy.addAdvice(0, adBarrier);
    ownerProxy.addAdvice(0, ownerBarrier);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<Outcome> outcomes = new ArrayList<>();
    try {
      Future<Outcome> cancellation = executor.submit(() -> run("CANCELAMENTO_CREDITO", () ->
          premium.cancelar(activation, "Cancelamento sintetico com compra concorrente", "credito-concorrente-cancelar",
              admin, "credito-concorrente-cancelar")));
      await(cancellationReachedFirstLock, "cancelamento alcancou primeiro lock");
      Future<Outcome> purchase = executor.submit(() -> run("COMPRA_CONCORRENTE", () ->
          purchasedCost.set(compras.comprar(new MinhaCompraPremiumRequest("fotos-extras-" + fixture.ad(),
              List.of(new MinhaCompraPremiumItemRequest("ANUNCIO_TOPO", 7))),
              "credito-concorrente-topo", auth, "credito-concorrente-topo").totalDebitado())));
      await(purchaseOwnsUser, "compra parada depois do lock real");
      String ownerSql = jdbc.queryForObject("select query from pg_stat_activity where pid=?", String.class, purchasePid.get());
      System.out.println("CANCELAMENTO_CREDITO_COMPRA_OWNER_LOCK_SQL=" + ownerSql.replaceAll("\\s+", " "));
      assertThat(ownerSql).containsIgnoringCase("usuario").containsIgnoringCase("for ");
      ownerSqlCaptured.countDown();
      outcomes.add(cancellation.get(30, TimeUnit.SECONDS));
      outcomes.add(purchase.get(30, TimeUnit.SECONDS));
    } finally {
      purchaseOwnsUser.countDown();
      ownerSqlCaptured.countDown();
      executor.shutdownNow();
      boolean stopped = executor.awaitTermination(10, TimeUnit.SECONDS);
      adProxy.removeAdvice(adBarrier);
      ownerProxy.removeAdvice(ownerBarrier);
      System.out.println("CANCELAMENTO_CREDITO_COMPRA_LOCK_ORDER=" + lockOrder);
      System.out.println("CANCELAMENTO_CREDITO_COMPRA_WORKERS_STOPPED=" + stopped);
      assertThat(stopped).isTrue();
    }
    System.out.println("CANCELAMENTO_CREDITO_COMPRA_FINAL_STATE=ativacao:"
        + value("select status from ativacao_beneficio where id=?", activation)
        + " saldo:" + ledger.consultarSaldo(fixture.owner())
        + " estornos:" + count("select count(*) from movimento_credito where usuario_id=? and tipo='ESTORNO'", fixture.owner()));
    assertThat(outcomes).hasSize(2).allSatisfy(outcome ->
        assertThat(outcome.failure()).as("%s SQLState=%s locks=%s", outcome.operation(), outcome.sqlStates(), lockOrder).isNull());
    assertThat(value("select status from ativacao_beneficio where id=?", activation)).isEqualTo("REVOGADA");
    assertThat(purchasedCost.get()).isPositive();
    assertThat(ledger.consultarSaldo(fixture.owner())).isEqualTo(100 - purchasedCost.get());
    assertThat(count("select count(*) from movimento_credito where usuario_id=? and tipo='ESTORNO'", fixture.owner())).isEqualTo(1);
    assertThat(count("""
        select count(*) from ativacao_beneficio a join beneficio_premium b on b.id=a.beneficio_id
        where a.anuncio_id=? and b.codigo='ANUNCIO_TOPO' and a.status='ATIVA'
        """, fixture.ad())).isEqualTo(1);
    System.out.println("CANCELAMENTO_CREDITO_COMPRA_RESULT=OK transactions=2 retries=0 refund=1 purchase=1");
  }

  private Outcome run(String name, Runnable action) {
    operation.set(name);
    try {
      action.run();
      System.out.println("FOTOS_EXTRAS_CANCELAMENTO_OPERATION=" + name + " result=COMMITTED SQLState=[]");
      return new Outcome(name, null, List.of());
    } catch (Throwable failure) {
      List<String> states = sqlStates(failure);
      System.out.println("FOTOS_EXTRAS_CANCELAMENTO_OPERATION=" + name + " result=FAILED SQLState=" + states
          + " exception=" + failure.getClass().getSimpleName());
      return new Outcome(name, failure, List.copyOf(states));
    } finally {
      operation.remove();
    }
  }

  private static List<String> sqlStates(Throwable failure) {
    List<String> states = new ArrayList<>();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sql.getSQLState() != null) states.add(sql.getSQLState());
    }
    return states;
  }

  private Fixture seed() throws Exception {
    UUID owner = user("ANUNCIANTE");
    UUID ad = UUID.randomUUID();
    // The disposable fixture applies V024, including the canonical catalogue
    // and its seven-day option. Administrative activation creates no debit.
    UUID benefit = jdbc.queryForObject(
        "select id from beneficio_premium where codigo='FOTOS_EXTRA_5' and escopo='ANUNCIO'", UUID.class);
    OffsetDateTime now = now();
    jdbc.update("""
        insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,criado_em,atualizado_em)
        values(?,?,?,'Fotos extras concorrencia','Conteudo sintetico','PUBLICADO','APROVADO','OUTROS',?,?)
        """, ad, owner, "fotos-extras-" + ad, now, now);
    byte[] bytes = png();
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    UUID fifth = null;
    for (int index = 0; index < 5; index++) {
      UUID file = UUID.randomUUID();
      UUID link = UUID.randomUUID();
      boolean pending = index == 4;
      String key = (pending ? "hml/midias-pendentes/" : "hml/midias-aprovadas/") + file + ".png";
      objects.put(objectKey(pending ? StorageArea.PRIVATE_MEDIA : StorageArea.PUBLIC_MEDIA, key),
          new StoredObject(bytes, "image/png"));
      jdbc.update("""
          insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
            sha256,status_arquivo,criado_em,pipeline_versao,marca_dagua_versao,processado_em,sha256_origem)
          values(?,'R2',?,?,'image/png',?,?,?, ?,1,'fixture-sintetica',?,?)
          """, file, pending ? "privadas" : "publicas", key, bytes.length, hash,
          pending ? "PENDENTE" : "VALIDADO", now, now, hash);
      jdbc.update("""
          insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
            visibilidade_midia,criado_em,atualizado_em)
          values(?,?,?,'FOTO','GALERIA',?,?,'LIVRE',?,?)
          """, link, ad, file, index, pending ? "PENDENTE" : "PUBLICAVEL", now, now);
      if (pending) fifth = link;
    }
    return new Fixture(owner, ad, benefit, fifth);
  }

  private UUID user(String type) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        insert into usuario(id,nome,email_normalizado,status,tipo_conta,criado_em,atualizado_em,versao)
        values(?,'Pessoa sintetica',?,'ATIVO',?,?,?,0)
        """, id, id + "@example.invalid", type, now(), now());
    return id;
  }

  private AdminUserPrincipal admin() {
    UUID id = user("STAFF");
    return new AdminUserPrincipal(id, "Admin sintetico", id + "@example.invalid", null,
        List.of(PapelUsuario.ADMIN), List.of(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);
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

  private static void await(CountDownLatch latch, String description) throws InterruptedException {
    assertThat(latch.await(20, TimeUnit.SECONDS)).as(description).isTrue();
  }

  private long count(String sql, Object... parameters) { return jdbc.queryForObject(sql, Long.class, parameters); }
  private String value(String sql, Object... parameters) { return jdbc.queryForObject(sql, String.class, parameters); }
  private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
  private static String objectKey(StorageArea area, String key) { return area + ":" + key; }
  private record Fixture(UUID owner, UUID ad, UUID benefit, UUID fifth) { }
  private record WindowCase(String status, OffsetDateTime start, OffsetDateTime end, OffsetDateTime revoked, String reason) { }
  private record Outcome(String operation, Throwable failure, List<String> sqlStates) { }
}
