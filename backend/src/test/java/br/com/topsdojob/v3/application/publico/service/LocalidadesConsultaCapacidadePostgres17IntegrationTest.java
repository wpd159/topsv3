package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumExpiracaoPolicyService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.projection.ArquivoPublicoLeitura;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageConfiguration;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Real PG17/JPA/pool5; separate signed R2 HTTP transport tests never feed SEO or persisted previews. */
@DataJpaTest(showSql = false, properties = {
        "logging.level.org.hibernate.stat=OFF",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class,
        initializers = LocalidadesConsultaCapacidadePostgres17IntegrationTest.CapacidadeInitializer.class)
@Import({LocalidadePublicaConsultaService.class, LocalidadesConsultaCoordenador.class,
        AnuncioSeoElegibilidadeConsultaService.class, AnuncioSeoIndexabilidadePolicy.class,
        LocalidadeSeoIndexabilidadePolicy.class, MidiaPublicaMapper.class, MidiaPublicaUrlService.class,
        PremiumPublicoMapper.class, BeneficioAnuncioConsultaService.class,
        PremiumExpiracaoPolicyService.class, FotoUploadProcessor.class,
        R2StorageConfiguration.class, LocalidadesConsultaCapacidadePostgres17IntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "LOCALIDADES_POSTGRES17_ENABLED", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class LocalidadesConsultaCapacidadePostgres17IntegrationTest {
    private static final String PREFIX = "hml/publicas/restritas-borradas/v1/";
    private static final UUID GO = UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID SP = UUID.fromString("71000000-0000-4000-8000-000000000002");
    private static final UUID CITY_GO = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID CITY_SP = UUID.fromString("72000000-0000-4000-8000-000000000002");
    private static final LocalS3 REMOTE = LocalS3.start();
    private static final ListTimeline TIMELINE = new ListTimeline();
    @Autowired private LocalidadePublicaConsultaService service;
    @SpyBean private LocalidadesConsultaCoordenador coordinator;
    @Autowired private HikariDataSource pool;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private R2VerificacaoAgrupadaPreviews verifier;
    @Autowired private R2StorageProperties properties;
    @Autowired private ObjectStorage objectStorage;
    @Autowired private ArquivoMidiaRepository arquivoRepository;
    @SpyBean private AnuncioSeoElegibilidadeConsultaService eligibility;
    @SpyBean private MidiaRestritaDerivacaoService derivation;
    @PersistenceContext private EntityManager em;
    private final List<ExecutorService> executors = new ArrayList<>();
    private final AtomicReference<Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado>> evaluated = new AtomicReference<>();
    private final AtomicReference<Set<String>> verifiedKeys = new AtomicReference<>();
    private final java.util.Random identities = new java.util.Random(0x0165656bL);
    private final LocalidadesConsultaPostgres17IntegrationTest.EvaluationBoundary evaluation =
            new LocalidadesConsultaPostgres17IntegrationTest.EvaluationBoundary();

    @BeforeEach
    void prepare() {
        // Compare booleans so a configuration failure never prints an unexpected credential or endpoint.
        assertThat(("http://127.0.0.1:" + REMOTE.server.getAddress().getPort()).equals(properties.getEndpoint()))
                .as("synthetic loopback endpoint survived configuration binding").isTrue();
        assertThat("synthetic-access".equals(properties.getAccessKey())).as("synthetic access key").isTrue();
        assertThat("synthetic-signing-value".equals(properties.getSigningValue())).as("synthetic signing value").isTrue();
        assertThat("publicas-teste".equals(properties.getPublicMediaBucket())).as("synthetic public bucket").isTrue();
        assertThat("privadas-teste".equals(properties.getPrivateMediaBucket())).as("synthetic private bucket").isTrue();
        assertThat("documentos-teste".equals(properties.getDocumentBucket())).as("synthetic document bucket").isTrue();
        assertThat("hml/publicas/".equals(properties.getPublicMediaPrefix())).as("synthetic public prefix").isTrue();
        assertThat("https://public.example.invalid".equals(properties.getPublicBaseUrl())).as("synthetic public origin").isTrue();
        drained();
        REMOTE.reset();
        REMOTE.pool = pool;
        reset(verifier, eligibility, derivation, coordinator);
        evaluation.reset();
        doAnswer(call -> {
            evaluation.beforeSnapshot();
            return call.callRealMethod();
        }).when(coordinator).transacao(anyString(), any());
        verifiedKeys.set(null);
        doAnswer(call -> {
            @SuppressWarnings("unchecked") Set<String> result = (Set<String>) call.callRealMethod();
            verifiedKeys.set(result);
            return result;
        }).when(verifier).verificar(anySet());
        // Observer only: every invocation still executes the real repository/mapper/policy.
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> result =
                    (Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado>) call.callRealMethod();
            evaluated.set(result);
            return result;
        }).when(eligibility).avaliarLocalidades(any(), any());
        evaluated.set(null);
        identities.setSeed(0x0165656bL);
        jdbc.execute("truncate usuario, estado, arquivo_midia cascade");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getConnectionTimeout()).isEqualTo(30_000);
        assertThat(jdbc.queryForObject("show server_version_num", Integer.class)).isBetween(170000, 179999);
    }

    @AfterEach
    void cleanup() throws Exception {
        REMOTE.release.countDown();
        evaluation.release();
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        drained();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(REMOTE.active.get()).isZero();
            assertThat(REMOTE.executor.getActiveCount()).isZero();
            assertThat(REMOTE.executor.getQueue()).isEmpty();
        });
        assertPoolFree();
        assertThat(REMOTE.failure.get()).isNull();
        assertThat(REMOTE.mutations.get()).isZero();
        for (String setting : List.of("statement_timeout", "lock_timeout", "transaction_timeout"))
            assertThat(jdbc.queryForObject("show " + setting, String.class)).isEqualTo("0");
        try (Connection connection = pool.getConnection()) { assertThat(connection.getNetworkTimeout()).isZero(); }
    }

    @AfterAll
    static void stopOwnedResources() throws Exception {
        try { REMOTE.close(); }
        finally {
            // A child JVM borrows only the controller's disposable DB; it does not own Docker resources.
            if (System.getenv("TOPS_COLD_JDBC") == null)
                LocalidadesConsultaPostgres17IntegrationTest.PostgresSupport.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {975, 1311})
    @Order(1)
    void inventarioRealistaConcluiPrimeiraENovasDescobertasSemCache(int previews) {
        Inventory fixture = seed(715, previews);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        int initialThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        DescobertaLocalidadesPublicaDto reference = null;
        for (int round = 1; round <= 3; round++) {
            Metrics before = metrics();
            long started = System.nanoTime();
            DescobertaLocalidadesPublicaDto response = service.descobrir();
            double duration = milliseconds(started);
            assertComplete(response, fixture.ads());
            if (reference == null) reference = response;
            else assertThat(response).isEqualTo(reference);
            assertNoPreviewCalls();
            assertThat(duration).isLessThan(3500.0); // Functional deadline: NO cleanup tolerance.
            assertThat(metrics().transactions - before.transactions).isEqualTo(1);
            assertThat(REMOTE.lists.get() - before.lists).isZero();
            assertThat(REMOTE.heads.get() - before.heads).isZero();
            assertThat(REMOTE.bytes.get() - before.bytes).isZero();
            assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
            assertThat(REMOTE.maxActive.get()).isZero();
            assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
            drained();
            evidence("healthy_" + previews + "_round_" + round, started, duration, before);
        }
        // Shared clients/executors may initialize lazily, but rounds do not create a queue/thread per preview.
        assertThat(REMOTE.executor.getPoolSize()).isLessThanOrEqualTo(4);
        System.out.printf("CAPACITY_THREADS initial=%d after=%d httpServerPool=%d serverQueue=%d%n",
                initialThreads, ManagementFactory.getThreadMXBean().getThreadCount(),
                REMOTE.executor.getPoolSize(), REMOTE.executor.getQueue().size());
    }

    @Test
    @Order(0)
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.MINUTES)
    void primeiraDescobertaPassaEmTresJvmsRepresentativasSeparadas() throws Exception {
        LocalidadesDescobertaFriaGate.executar(pool);
    }

    @Test
    @Order(0)
    void replayAConcluiDtoInteiroNaPrimeiraDescobertaENoProcessoReutilizado() {
        Inventory fixture = seed(715, 975);
        // Differential replay of the recorded A profile, not a forecast of future R2 latency.
        REMOTE.configure(fixture.keys(), 1358, 1839, 837);
        DescobertaLocalidadesPublicaDto reference = null;
        for (int round = 1; round <= 3; round++) {
            Metrics before = metrics();
            long started = System.nanoTime();
            DescobertaLocalidadesPublicaDto response = service.descobrir();
            double duration = milliseconds(started);
            assertComplete(response, fixture.ads());
            if (reference == null) reference = response;
            else assertThat(response).isEqualTo(reference);
            assertNoPreviewCalls();
            assertThat(duration).isLessThan(3500.0);
            assertThat(metrics().transactions - before.transactions).isEqualTo(1);
            assertThat(REMOTE.lists.get() - before.lists).isZero();
            assertThat(REMOTE.bytes.get() - before.bytes).isZero();
            assertThat(REMOTE.heads.get() - before.heads).isZero();
            assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
            assertThat(REMOTE.maxActive.get()).isZero();
            assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
            drained();
            evidence("replay_a_1839_837_round_" + round, started, duration, before);
        }
    }

    @Test
    void inventarioComTerceiraPaginaNaoParticipaDaDescoberta() {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 2358, 1250, 800, 800);
        Metrics before = metrics();
        long started = System.nanoTime();
        DescobertaLocalidadesPublicaDto response = service.descobrir();
        double duration = milliseconds(started);
        assertComplete(response, fixture.ads());
        assertThat(duration).isLessThan(3500.0);
        assertNoPreviewCalls();
        assertThat(metrics().transactions - before.transactions).isEqualTo(1);
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("three_pages_with_unrelated_objects", started, duration, before);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void consumidoresGlobaisECidadesCompartilhamAvaliacaoRealSemPreview(int count) throws Exception {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        var gate = evaluation.block(false);
        Metrics before = metrics();
        long started = System.nanoTime();
        List<Future<Object>> consumers = consumers(count);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == count);
        assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
        assertPoolFree();
        assertThat(observerCount("state in ('active','idle in transaction')")).isZero();
        gate.release.countDown();
        for (int i = 0; i < count; i++) assertConsumer(i, consumers.get(i).get(3500, TimeUnit.MILLISECONDS), fixture.ads());
        double duration = milliseconds(started);
        assertThat(duration).isLessThan(3500.0);
        assertNoPreviewCalls();
        assertThat(metrics().transactions - before.transactions).isEqualTo(1);
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("consumers_" + count, started, duration, before);
    }

    @Test
    void headSerialRealDe240msContinuaReprovadoSomenteNaCamadaPreview() {
        Inventory fixture = seed(715, 975);
        List<UUID> files = previewFiles();
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.headDelayMs = 240;
        doAnswer(call -> {
            Set<String> requested = call.getArgument(0);
            Set<String> found = new LinkedHashSet<>();
            for (String key : requested) if (objectStorage.exists(StorageArea.PUBLIC_MEDIA, key)) found.add(key);
            return Set.copyOf(found);
        }).when(verifier).verificar(anySet());
        Metrics before = metrics();
        long started = System.nanoTime();
        assertThat(status(() -> previewOperation(files))).isEqualTo(503);
        double duration = milliseconds(started);
        drained();
        assertThat(duration).isBetween(3400.0, 4000.0);
        assertThat(REMOTE.heads.get()).isBetween(1, 16).isLessThan(975);
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(metrics().transactions - before.transactions).isZero();
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("preview_serial_head_240ms_expected_503", started, duration, before);
        await().atMost(Duration.ofSeconds(1)).until(() -> REMOTE.active.get() == 0);
        REMOTE.reset();
        assertComplete(service.descobrir(), fixture.ads());
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.lists.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void consumidoresAguardamAvaliacaoNoSnapshotComUmaUnicaConexaoDoProdutor(int count) throws Exception {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 1358, 1839, 837);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isTrue();
            assertThat(jdbc.queryForObject("show transaction_isolation", String.class)).isEqualTo("repeatable read");
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            @SuppressWarnings("unchecked")
            Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado> result =
                    (Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado>) call.callRealMethod();
            evaluated.set(result);
            return result;
        }).when(eligibility).avaliarLocalidades(any(), any());
        Metrics before = metrics();
        long started = System.nanoTime();
        List<Future<Object>> consumers = consumers(count);
        try {
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == count);
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
            assertThat(observerCount("state in ('active','idle in transaction')")).isEqualTo(1);
            assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
        } finally {
            release.countDown();
        }
        for (int i = 0; i < count; i++) assertConsumer(i, consumers.get(i).get(3500, TimeUnit.MILLISECONDS), fixture.ads());
        assertThat(milliseconds(started)).isLessThan(3500);
        assertThat(metrics().transactions - before.transactions).isEqualTo(1);
        assertNoPreviewCalls();
        drained();
        assertPoolFree();
        evidence("consumers_" + count + "_inside_repeatable_read", started, milliseconds(started), before);
    }

    @Test
    void paginacaoRealSeparadaEncontraProvasAposTerceiraPagina() {
        Inventory fixture = seed(715, 1311);
        List<UUID> files = previewFiles();
        REMOTE.configure(fixture.keys(), 2358, 1250, 800, 800);
        TIMELINE.begin("three_pages_1311");
        Throwable failure = null;
        try {
            long started = System.nanoTime();
            Set<String> response;
            try { response = previewOperation(files); }
            finally { TIMELINE.operationDone(); }
            assertThat(response).hasSize(1311);
            assertThat(response).containsExactlyInAnyOrderElementsOf(fixture.keys());
            assertThat(verifiedKeys.get()).containsExactlyInAnyOrderElementsOf(fixture.keys());
            assertThat(milliseconds(started)).isLessThan(3500);
            assertThat(REMOTE.lists.get()).isEqualTo(3);
            assertThat(REMOTE.bytes.get()).isGreaterThanOrEqualTo(690_000);
            assertThat(REMOTE.heads.get()).isZero();
            assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            TIMELINE.report(failure);
        }
    }

    @Test
    void ausenciasExatasEDuplicatasNaoViraramCacheOuCorrespondenciaParcial() {
        Inventory fixture = seed(3, 3);
        UUID absentAd = fixture.ads().get(0);
        String absent = fixture.keyByAd().get(absentAd);
        List<UUID> files = previewFiles();
        Set<String> present = new LinkedHashSet<>(fixture.keys());
        present.remove(absent);
        present.add(PREFIX + absent.substring(PREFIX.length()).toUpperCase(Locale.ROOT));
        present.add(absent + ".original");
        present.add(PREFIX + "original-" + absent.substring(PREFIX.length()));
        REMOTE.configure(present, 1358, 120, 120);
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
        var first = previewOperation(files);
        assertThat(first).doesNotContain(absent).hasSize(2);
        assertThat(verifiedKeys.get()).doesNotContain(absent).hasSize(2);
        assertThat(REMOTE.lists.get()).isEqualTo(2); // Absence requires exhaustion.
        assertThat(REMOTE.heads.get()).isZero();

        // Explicit duplicate identities belong to this preview operation, not to discovery.
        UUID shared = fixture.fileByAd().get(fixture.ads().get(1));
        REMOTE.configure(present, 1358, 120, 120);
        previewOperation(List.of(shared, shared, fixture.fileByAd().get(fixture.ads().get(2))));
        @SuppressWarnings("unchecked")
        Set<String> lastRequested = (Set<String>) org.mockito.Mockito.mockingDetails(verifier).getInvocations()
                .stream().filter(call -> call.getMethod().getName().equals("verificar")).reduce((a, b) -> b)
                .orElseThrow().getArgument(0);
        assertThat(lastRequested).hasSize(2);
        REMOTE.configure(Set.of(), 1358, 120, 120);
        assertThat(previewOperation(files)).isEmpty();
        assertThat(verifiedKeys.get()).isEmpty();
        assertComplete(service.descobrir(), fixture.ads());
    }

    @Test
    void catalogoLegitimamenteVazioNaoExecutaStorage() {
        assertThat(service.descobrir().estados()).isEmpty();
        assertNoPreviewCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {"403", "429", "500", "malformed", "missing_token", "repeated_token", "incomplete"})
    void erroDeInventarioFalhaNaCamadaPreviewMasNaoNaDescoberta(String fault) {
        Inventory fixture = seed(3, 3);
        List<UUID> files = previewFiles();
        REMOTE.configure(fixture.keys(), 1358, 120, 120);
        REMOTE.fault = fault;
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
        long started = System.nanoTime();
        assertThat(status(() -> previewOperation(files))).isEqualTo(503);
        assertThat(milliseconds(started)).isLessThan(3500.0);
        drained();
        assertPoolFree();
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.lists.get()).isBetween(1, 8);
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        int failedCalls = REMOTE.lists.get();
        REMOTE.fault = "";
        assertThat(previewOperation(files)).containsExactlyInAnyOrderElementsOf(fixture.keys());
        assertThat(REMOTE.lists.get() - failedCalls).isEqualTo(2);
        System.out.printf("CAPACITY_PREVIEW_NEGATIVE fault=%s previewStatus=503 discoveryStatus=200 recovery=complete listsFailed=%d heads=0%n", fault, failedCalls);
    }

    @Test
    void limiteDeIdentidadesPreviewContinuaFalhandoSemHttp() {
        List<UUID> files = new ArrayList<>();
        for (int i = 0; i < 4097; i++) files.add(nextIdentity());
        assertThat(status(() -> previewOperation(files))).isEqualTo(503);
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(REMOTE.heads.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"publication", "owner", "moderation", "removal", "media", "file",
            "identity", "identity_listed_extra", "new_media", "free_to_restricted", "restricted_to_free", "location"})
    void snapshotDefinitivoObservaMudancasConfirmadasESemCacheEntreDescobertas(String change) throws Exception {
        Inventory fixture = seed(3, 3);
        UUID ad = fixture.ads().get(0);
        UUID file = fixture.fileByAd().get(ad);
        REMOTE.configure(fixture.keys(), 1358, 1839, 837);
        assertComplete(service.descobrir(), fixture.ads());
        var gate = evaluation.block(false);
        Future<DescobertaLocalidadesPublicaDto> result = executor(1).submit(service::descobrir);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolFree();
        switch (change) {
            case "publication" -> observerUpdate("update anuncio set status='PAUSADO' where id=?", ad);
            case "owner" -> observerUpdate("update usuario set status='DESATIVADO' where id=(select usuario_id from anuncio where id=?)", ad);
            case "moderation" -> observerUpdate("update anuncio set status_moderacao='REJEITADO' where id=?", ad);
            case "removal" -> observerUpdate("update anuncio set removido_em=now() where id=?", ad);
            case "media" -> observerUpdate("update anuncio_midia set status='REJEITADA' where anuncio_id=? and ordem=1", ad);
            case "file" -> observerUpdate("update arquivo_midia set status_arquivo='REMOVIDO' where id=(select arquivo_midia_id from anuncio_midia where anuncio_id=? and ordem=1)", ad);
            case "new_media" -> photo(ad, 2, true);
            case "free_to_restricted" -> observerUpdate("update anuncio_midia set visibilidade_midia='RESTRITA_18' where anuncio_id=? and ordem=1", ad);
            case "restricted_to_free" -> observerUpdate("update anuncio_midia set visibilidade_midia='LIVRE' where anuncio_id=? and ordem=0", ad);
            case "location" -> observerUpdate("delete from anuncio_localizacao where anuncio_id=?", ad);
            default -> observerUpdate("update arquivo_midia set sha256=? where id=?", "a".repeat(64), file);
        }
        gate.release.countDown();
        var response = result.get(3500, TimeUnit.MILLISECONDS);
        boolean staysEligible = change.startsWith("identity") || change.equals("new_media") || change.equals("restricted_to_free");
        assertEligibleIds(staysEligible ? fixture.ads() : fixture.ads().subList(1, 3));
        boolean excludedPublic = Set.of("publication", "owner", "moderation", "removal", "location").contains(change);
        assertThat(response.estados().stream().mapToLong(value -> value.totalAnunciosAtivos()).sum())
                .isEqualTo(excludedPublic ? 2 : 3);
        service.descobrir(); // A fresh completed discovery must see the same committed state.
        assertEligibleIds(staysEligible ? fixture.ads() : fixture.ads().subList(1, 3));
        assertNoPreviewCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {"checksum", "new_identity"})
    void identidadeAlteradaNaoHerdaEstadoPersistidoMesmoSeObjetoNovoExiste(String change) {
        Inventory fixture = seed(3, 3);
        List<UUID> files = previewFiles();
        UUID changed = files.get(0), newFile = nextIdentity();
        Set<String> inventory = new LinkedHashSet<>(fixture.keys());
        inventory.add(previewKey(changed, "b".repeat(64)));
        inventory.add(previewKey(newFile, null));
        REMOTE.configure(inventory, 1358, 120, 120);
        String originalKey = previewKey(changed, null);
        jdbc.update("""
                update arquivo_midia set preview_restrito_tipo='PREVIEW_RESTRITO',
                    preview_restrito_chave=?,preview_restrito_pipeline_versao='v1',
                    preview_restrito_status='DISPONIVEL',preview_restrito_confirmado_em=now() where id=?
                """, originalKey, changed);
        var initial = derivation.resolverPreviewPublicaLeitura(
                ArquivoPublicoLeitura.de(arquivoRepository.findById(changed).orElseThrow()));
        assertThat(initial.previewUrl()).isEqualTo("https://public.example.invalid/" + originalKey);
        UUID current;
        if (change.equals("checksum")) {
            jdbc.update("update arquivo_midia set sha256=? where id=?", "b".repeat(64), changed);
            current = changed;
        } else {
            jdbc.update("""
                    insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,
                        status_arquivo,criado_em,preview_restrito_tipo,preview_restrito_chave,
                        preview_restrito_pipeline_versao,preview_restrito_status,preview_restrito_confirmado_em)
                    select ?,storage_provider,bucket,?,mime_type,tamanho_bytes,status_arquivo,now(),
                        preview_restrito_tipo,preview_restrito_chave,preview_restrito_pipeline_versao,
                        preview_restrito_status,preview_restrito_confirmado_em from arquivo_midia where id=?
                    """, newFile, "hml/privadas/" + newFile + ".jpg", changed);
            current = newFile;
        }
        var rejected = derivation.resolverPreviewPublicaLeitura(
                ArquivoPublicoLeitura.de(arquivoRepository.findById(current).orElseThrow()));
        assertThat(rejected.previewUrl()).isNull();
        assertThat(rejected.pendencia()).isEqualTo(MidiaRestritaDerivacaoService.PENDENTE_DERIVACAO_RESTRITA);
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(REMOTE.heads.get()).isZero();
        assertComplete(service.descobrir(), fixture.ads());
    }

    @Test
    void trocaLivreRestritaLivreReavaliaOMesmoArquivoPublicoEntreDescobertas() {
        Inventory fixture = seed(1, 1);
        UUID ad = fixture.ads().get(0);
        UUID freeLink = jdbc.queryForObject(
                "select id from anuncio_midia where anuncio_id=? and ordem=1", UUID.class, ad);
        // The same selected, validated public file remains unchanged throughout all snapshots.
        // The first restricted fixture image never satisfies the anonymous photo requirement.
        assertThat(jdbc.queryForObject("""
                select count(*) from anuncio_midia m join arquivo_midia a on a.id=m.arquivo_midia_id
                where m.id=? and a.status_arquivo='VALIDADO' and a.bucket='publicas-teste'
                    and a.chave_objeto like 'hml/publicas/%'
                """, Integer.class, freeLink)).isEqualTo(1);
        REMOTE.configure(fixture.keys(), 1358, 1839, 837);
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
        jdbc.update("update anuncio_midia set visibilidade_midia='RESTRITA_18' where id=?", freeLink);
        var restricted = service.descobrir();
        assertThat(restricted.estados()).singleElement()
                .satisfies(state -> assertThat(state.totalAnunciosAtivos()).isEqualTo(1));
        assertEligibleIds(List.of());
        assertNoPreviewCalls();
        jdbc.update("update anuncio_midia set visibilidade_midia='LIVRE' where id=?", freeLink);
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gain", "loss", "expiry"})
    void beneficioConfirmadoEntreDescobertasReavaliaFotoLivreNaQuintaPosicao(String change) throws Exception {
        Inventory fixture = seed(1, 1);
        UUID ad = fixture.ads().get(0);
        jdbc.update("update anuncio_midia set visibilidade_midia='RESTRITA_18' where anuncio_id=? and ordem=1", ad);
        photo(ad, 2, true);
        photo(ad, 3, true);
        photo(ad, 4, false); // Independent oracle: only this fifth image can satisfy photo eligibility.
        UUID user = jdbc.queryForObject("select usuario_id from anuncio where id=?", UUID.class, ad);
        UUID group = nextIdentity(), activation = nextIdentity();
        jdbc.update("""
                insert into grupo_ativacao_beneficio(id,tipo,origem,usuario_id,anuncio_id,
                validade_inicio_em,validade_fim_em,status,criado_em,atualizado_em)
                values (?,'PACOTE','ADMIN',?,?,now()-interval '1 day',now()+interval '2 days','ATIVO',now(),now())
                """, group, user, ad);
        jdbc.update("""
                insert into ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,grupo_ativacao_id,
                origem,inicio_em,fim_em,status,custo_creditos_snapshot,criado_em)
                select ?,id,?,?,?,'ADMIN',now()-interval '1 hour',now()+interval '1 day',?,0,now()
                from beneficio_premium where codigo='FOTOS_EXTRA_5'
                """, activation, user, ad, group, change.equals("gain") ? "AGENDADA" : "ATIVA");
        service.descobrir();
        assertEligibleIds(change.equals("gain") ? List.of() : fixture.ads());
        var gate = evaluation.block(false);
        Future<DescobertaLocalidadesPublicaDto> result = executor(1).submit(service::descobrir);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolFree();
        if (change.equals("expiry")) observerUpdate("update ativacao_beneficio set fim_em=now()-interval '1 minute' where id=?", activation);
        else observerUpdate("update ativacao_beneficio set status=? where id=?",
                change.equals("gain") ? "ATIVA" : "REVOGADA", activation);
        gate.release.countDown();
        result.get(3500, TimeUnit.MILLISECONDS);
        assertEligibleIds(change.equals("gain") ? fixture.ads() : List.of());
        service.descobrir();
        assertEligibleIds(change.equals("gain") ? fixture.ads() : List.of());
        assertNoPreviewCalls();
    }

    @Test
    void timeoutHttpRealNaCamadaPreviewNaoMantemProdutorOuJdbcERecupera() throws Exception {
        Inventory fixture = seed(715, 1311);
        List<UUID> files = previewFiles();
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
        REMOTE.gatePage = 2;
        Metrics before = metrics();
        long started = System.nanoTime();
        Future<Integer> response = executor(1).submit(() -> status(() -> previewOperation(files)));
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolFree();
        assertThat(response.get(4, TimeUnit.SECONDS)).isEqualTo(503);
        double duration = milliseconds(started);
        assertThat(duration).isLessThanOrEqualTo(4000.0);
        drained();
        assertThat(REMOTE.active.get()).isEqualTo(1); // Deliberately held remote handler is not local client work.
        assertPoolFree();
        REMOTE.release.countDown();
        await().atMost(Duration.ofSeconds(2)).until(() -> REMOTE.active.get() == 0);
        evidence("preview_timeout_second_page", started, duration, before);
        REMOTE.gatePage = 0;
        assertThat(previewOperation(files)).hasSize(1311);
        assertComplete(service.descobrir(), fixture.ads());
    }

    @Test
    void cancelamentoPreviewInterrompeClienteELiberaProximaOperacao() throws Exception {
        Inventory fixture = seed(3, 3);
        List<UUID> files = previewFiles();
        REMOTE.configure(fixture.keys(), 1358, 120, 120);
        REMOTE.gatePage = 1;
        Future<?> response = executor(1).submit(() -> previewOperation(files));
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(response.cancel(true)).isTrue();
        drained();
        assertPoolFree();
        REMOTE.release.countDown();
        await().atMost(Duration.ofSeconds(2)).until(() -> REMOTE.active.get() == 0);
        REMOTE.gatePage = 0;
        assertThat(previewOperation(files)).hasSize(3);
    }

    @Test
    void desistenciasNaoCriamAvaliacaoPorConsumidorEClienteSeguinteRecupera() throws Exception {
        Inventory fixture = seed(715, 975);
        REMOTE.configure(fixture.keys(), 1358, 1839, 837);
        var gate = evaluation.block(false);
        long producers = coordinator.produtoresIniciados();
        List<Future<Object>> consumers = consumers(6);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 6);
        assertPoolFree();
        assertThat(consumers.get(0).cancel(true)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 5);
        assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
        gate.release.countDown();
        for (int i = 1; i < 6; i++) assertConsumer(i, consumers.get(i).get(3500, TimeUnit.MILLISECONDS), fixture.ads());
        assertThat(coordinator.produtoresIniciados() - producers).isEqualTo(1);
        drained();
        gate = evaluation.block(false);
        consumers = consumers(6);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 6);
        for (Future<Object> consumer : consumers) assertThat(consumer.cancel(true)).isTrue();
        drained();
        assertPoolFree();
        gate.release.countDown();
        assertComplete(service.descobrir(), fixture.ads());
        assertNoPreviewCalls();
    }

    private List<UUID> previewFiles() {
        return jdbc.queryForList("select arquivo_midia_id from anuncio_midia where visibilidade_midia='RESTRITA_18' order by id", UUID.class);
    }

    private Set<String> previewOperation(List<UUID> files) {
        // Explicit transport-layer operation, not a public-preview resolver or SEO
        // context. Keys are independent fixture identities; JDBC is not held here.
        Set<String> requested = new LinkedHashSet<>();
        files.forEach(id -> requested.add(previewKey(id, null)));
        return coordinator.executar("r2_transport", () -> verifier.verificar(requested));
    }

    private void assertNoPreviewCalls() {
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.bytes.get()).isZero();
        assertThat(REMOTE.maxActive.get()).isZero();
        verify(verifier, never()).verificar(anySet());
        verify(derivation, never()).resolverPreviewPublica(any());
        verify(derivation, never()).resolverPreviewPublicaLeitura(any());
    }

    private Inventory seed(int ads, int previews) {
        jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values (?,'GO','Goias','goias',now()),(?,'SP','Sao Paulo','sao paulo',now())", GO, SP);
        jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values (?,?,'Central Goias','central goias','central',now()),(?,?,'Central Paulista','central paulista','central',now())", CITY_GO, GO, CITY_SP, SP);
        List<UUID> ids = new ArrayList<>();
        Map<UUID, String> keyByAd = new LinkedHashMap<>();
        Map<UUID, UUID> fileByAd = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < ads; i++) {
            UUID user = nextIdentity(), ad = nextIdentity();
            ids.add(ad);
            jdbc.update("insert into usuario(id,nome,status,tipo_conta,criado_em,atualizado_em,versao) values (?,'Pessoa sintetica','ATIVO','ANUNCIANTE',now(),now(),0)", user);
            jdbc.update("""
                    insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,
                    atendimento_exclusivamente_virtual,publicado_em,criado_em,atualizado_em,versao)
                    values (?,?,?,'Companhia sintetica local',?,'PUBLICADO','APROVADO','ACOMPANHANTE_FEMININA',false,now(),now(),now(),0)
                    """, ad, user, "capacidade-" + ad, "Descricao ficticia segura para validar localidades. ".repeat(4));
            boolean go = i % 2 == 0;
            jdbc.update("insert into anuncio_localizacao(anuncio_id,estado_id,cidade_id,criado_em,atualizado_em) values (?,?,?,now(),now())", ad, go ? GO : SP, go ? CITY_GO : CITY_SP);
            UUID first = photo(ad, 0, true);
            photo(ad, 1, false);
            String key = previewKey(first, null);
            keys.add(key); keyByAd.put(ad, key); fileByAd.put(ad, first);
            if (i < previews - ads) {
                UUID extra = photo(ad, 2, true);
                keys.add(previewKey(extra, null));
            }
        }
        assertThat(keys).hasSize(previews);
        assertThat(jdbc.queryForObject("select count(*) from anuncio", Integer.class)).isEqualTo(ads);
        assertThat(jdbc.queryForObject("select count(distinct arquivo_midia_id) from anuncio_midia where visibilidade_midia='RESTRITA_18'", Integer.class)).isEqualTo(previews);
        return new Inventory(ids, keys, keyByAd, fileByAd);
    }

    private UUID photo(UUID ad, int order, boolean restricted) {
        UUID file = nextIdentity();
        jdbc.update("""
                insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                values (?,'R2',?,?,'image/jpeg',1024,'VALIDADO',now())
                """, file, restricted ? "privadas-teste" : "publicas-teste", (restricted ? "hml/privadas/" : "hml/publicas/") + file + ".jpg");
        jdbc.update("""
                insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,visibilidade_midia,criado_em,atualizado_em)
                values (?,?,?,'FOTO','GALERIA',?,'PUBLICAVEL',?,now(),now())
                """, nextIdentity(), ad, file, order, restricted ? "RESTRITA_18" : "LIVRE");
        return file;
    }

    private String previewKey(UUID id, String checksum) {
        // Independent fixture oracle: seeding must not prime the runtime derivation.
        String canonical = id + ":" + (checksum == null ? "sem-checksum" : checksum.trim().toLowerCase(Locale.ROOT)) + ":v1";
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return PREFIX + java.util.HexFormat.of().formatHex(digest).substring(0, 32) + ".jpg";
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    private UUID nextIdentity() { return new UUID(identities.nextLong(), identities.nextLong()); }

    private void assertComplete(DescobertaLocalidadesPublicaDto result, List<UUID> expected) {
        assertThat(result.estados().stream().mapToLong(state -> state.totalAnunciosAtivos()).sum()).isEqualTo(expected.size());
        assertThat(result.estados().stream().mapToLong(state -> state.indexacao().anunciosElegiveisUnicos()).sum()).isEqualTo(expected.size());
        // Seed order alternates GO/SP. These explicit values are an independent DTO oracle,
        // not a second invocation of the production locality policy or aggregator.
        assertThat(result.estados()).extracting(state -> state.uf())
                .containsExactlyElementsOf(expected.isEmpty() ? List.of()
                        : expected.size() == 1 ? List.of("GO") : List.of("GO", "SP"));
        for (var state : result.estados()) {
            boolean go = state.uf().equals("GO");
            long count = go ? (expected.size() + 1L) / 2 : expected.size() / 2L;
            boolean indexable = count >= 5;
            assertThat(state.nome()).isEqualTo(go ? "Goias" : "Sao Paulo");
            assertThat(state.totalAnunciosAtivos()).isEqualTo(count);
            assertThat(state.ultimaAtualizacao()).isNotNull();
            assertThat(state.indexacao()).isEqualTo(
                    new br.com.topsdojob.v3.application.publico.dto.IndexacaoLocalidadePublicaDto(
                            indexable, indexable ? "CIDADE_INDEXAVEL" : "SEM_CIDADE_INDEXAVEL", count, 5, true));
            assertThat(state.cidades()).singleElement().satisfies(city -> {
                assertThat(city.nome()).isEqualTo(go ? "Central Goias" : "Central Paulista");
                assertThat(city.slug()).isEqualTo("central");
                assertThat(city.totalAnunciosAtivos()).isEqualTo(count);
                assertThat(city.ultimaAtualizacao()).isNotNull();
                assertThat(city.bairros()).isEmpty();
                assertThat(city.indexacao()).isEqualTo(
                        new br.com.topsdojob.v3.application.publico.dto.IndexacaoLocalidadePublicaDto(
                                indexable, indexable ? "INVENTARIO_SUFICIENTE" : "INVENTARIO_INSUFICIENTE", count, 5, true));
            });
        }
        assertEligibleIds(expected);
    }
    private void assertEligibleIds(List<UUID> expected) {
        assertThat(evaluated.get()).isNotNull();
        assertThat(evaluated.get().entrySet().stream().filter(entry -> entry.getValue().indexavel()).map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
    }
    private List<Future<Object>> consumers(int count) {
        ExecutorService executor = executor(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await();
                return switch (index % 3) {
                    case 0 -> service.descobrir();
                    case 1 -> service.agregadoCidade("GO", "central");
                    default -> service.agregadoCidade("SP", "central");
                };
            }));
        }
        start.countDown();
        return futures;
    }
    private void assertConsumer(int index, Object response, List<UUID> ids) {
        if (index % 3 == 0) assertComplete((DescobertaLocalidadesPublicaDto) response, ids);
        else {
            AgregadoCidadePublicaDto city = (AgregadoCidadePublicaDto) response;
            assertThat(city.estadoUf()).isEqualTo(index % 3 == 1 ? "GO" : "SP");
            assertThat(city.totalAnunciosAtivos()).isEqualTo(index % 3 == 1 ? (ids.size() + 1) / 2 : ids.size() / 2);
            assertThat(city.indexacao().anunciosElegiveisUnicos()).isEqualTo(city.totalAnunciosAtivos());
            assertThat(city.estadoNome()).isEqualTo(index % 3 == 1 ? "Goias" : "Sao Paulo");
            assertThat(city.cidadeNome()).isEqualTo(index % 3 == 1 ? "Central Goias" : "Central Paulista");
            assertThat(city.cidadeSlug()).isEqualTo("central");
            assertThat(city.ultimaAtualizacao()).isNotNull();
            assertThat(city.bairros()).isEmpty();
            assertThat(city.cidadesRelacionadas()).isEmpty();
            assertThat(city.categorias()).containsExactly(
                    new br.com.topsdojob.v3.application.publico.dto.CategoriaCidadePublicaDto(
                            "ACOMPANHANTE_FEMININA", "Acompanhante Feminina", city.totalAnunciosAtivos()));
            assertThat(city.indexacao()).isEqualTo(
                    new br.com.topsdojob.v3.application.publico.dto.IndexacaoLocalidadePublicaDto(
                            true, "INVENTARIO_SUFICIENTE", city.totalAnunciosAtivos(), 5, true));
        }
    }
    private ExecutorService executor(int count) { ExecutorService result = Executors.newFixedThreadPool(count); executors.add(result); return result; }
    private void drained() {
        await().pollInterval(Duration.ofMillis(5)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(coordinator.trabalhoRemanescente()).isZero();
            assertThat(coordinator.possuiExecucaoEmVoo()).isFalse();
            assertThat(coordinator.consumidoresAguardando()).isZero();
        });
    }
    private void assertPoolFree() { assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero(); assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero(); }
    private static void observerUpdate(String sql, Object... arguments) {
        try (Connection connection = LocalidadesConsultaPostgres17IntegrationTest.PostgresSupport.observer();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            assertThat(statement.executeUpdate()).isPositive();
        } catch (Exception error) { throw new AssertionError("synthetic observer mutation failed", error); }
    }
    private long observerCount(String predicate) {
        try (Connection connection = LocalidadesConsultaPostgres17IntegrationTest.PostgresSupport.observer();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select count(*) from pg_stat_activity where application_name='localidades-it' and " + predicate)) {
            assertThat(result.next()).isTrue(); return result.getLong(1);
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private int status(Supplier<?> operation) { try { operation.get(); return 200; } catch (ResponseStatusException error) { return error.getStatusCode().value(); } }
    private Metrics metrics() {
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        return new Metrics(coordinator.produtoresIniciados(), statistics.getTransactionCount(), statistics.getPrepareStatementCount(), REMOTE.heads.get(), REMOTE.lists.get(), REMOTE.bytes.get());
    }
    private void evidence(String scenario, long start, double functional, Metrics before) {
        Metrics after = metrics();
        System.out.printf(Locale.ROOT,
                "CAPACITY_REAL_HTTP scenario=%s functionalMs=%.3f marginMs=%.3f withCleanupMs=%.3f producers=%d tx=%d sql=%d heads=%d lists=%d bytes=%d maxHttp=%d jdbcDuringRemote=%d poolActive=%d poolWaiting=%d remaining=%d serverActive=%d heapUsed=%d%n",
                scenario, functional, 3500d - functional, milliseconds(start), after.producers - before.producers,
                after.transactions - before.transactions, after.sql - before.sql, after.heads - before.heads,
                after.lists - before.lists, after.bytes - before.bytes, REMOTE.maxActive.get(), REMOTE.jdbcWhileWaiting.get(),
                pool.getHikariPoolMXBean().getActiveConnections(), pool.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                coordinator.trabalhoRemanescente(), REMOTE.active.get(), ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }
    private static double milliseconds(long start) { return (System.nanoTime() - start) / 1_000_000d; }
    private record Inventory(List<UUID> ads, Set<String> keys, Map<UUID, String> keyByAd, Map<UUID, UUID> fileByAd) { }
    private record Metrics(long producers, long transactions, long sql, int heads, int lists, long bytes) { }

    static final class CapacidadeInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            if (!LocalidadesDescobertaFriaProcesso.configurarJdbc(context))
                new LocalidadesConsultaPostgres17IntegrationTest.PostgresInitializer().initialize(context);
            // The binding source must contain the local values; programmatic bean setters alone are overwritten.
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("localidades-capacidade-r2",
                    Map.ofEntries(
                            Map.entry("app.storage.r2.enabled", "true"),
                            Map.entry("app.storage.r2.endpoint", "http://127.0.0.1:" + REMOTE.server.getAddress().getPort()),
                            Map.entry("app.storage.r2.region", "auto"),
                            Map.entry("app.storage.r2.access-key", "synthetic-access"),
                            Map.entry("app.storage.r2.signing-value", "synthetic-signing-value"),
                            Map.entry("app.storage.r2.public-media-bucket", "publicas-teste"),
                            Map.entry("app.storage.r2.private-media-bucket", "privadas-teste"),
                            Map.entry("app.storage.r2.document-bucket", "documentos-teste"),
                            Map.entry("app.storage.r2.public-media-prefix", "hml/publicas/"),
                            Map.entry("app.storage.r2.private-media-prefix", "hml/privadas/"),
                            Map.entry("app.storage.r2.document-prefix", "hml/documentos/"),
                            Map.entry("app.storage.r2.public-base-url", "https://public.example.invalid"))));
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties(MidiaUploadProperties.class)
    static class TestBeans {
        @Bean @Primary R2StorageProperties localR2Properties() {
            R2StorageProperties properties = new R2StorageProperties();
            properties.setEnabled(true);
            properties.setEndpoint("https://127.0.0.1:" + REMOTE.server.getAddress().getPort());
            properties.setAccessKey("synthetic-access"); properties.setSigningValue("synthetic-signing-value");
            properties.setPublicMediaBucket("publicas-teste"); properties.setPrivateMediaBucket("privadas-teste"); properties.setDocumentBucket("documentos-teste");
            properties.setPublicMediaPrefix("hml/publicas/"); properties.setPrivateMediaPrefix("hml/privadas/"); properties.setDocumentPrefix("hml/documentos/");
            properties.setPublicBaseUrl("https://public.example.invalid");
            properties.validateConfigured();
            properties.setEndpoint("http://127.0.0.1:" + REMOTE.server.getAddress().getPort());
            R2StorageProperties local = spy(properties);
            // Test transport only: production configuration still requires HTTPS unchanged.
            doNothing().when(local).validateConfigured();
            return local;
        }
        @Bean R2VerificacaoAgrupadaPreviews realGroupedVerifier(R2StorageProperties properties) {
            // Same production constructor; locality discovery must not enter its remote path.
            return spy(new R2VerificacaoAgrupadaPreviews(properties, null, TIMELINE));
        }
        @Bean MidiaRestritaDerivacaoService derivation(ObjectProvider<ObjectStorage> storage,
                R2StorageProperties properties, FotoUploadProcessor processor, ArquivoMidiaRepository repository) {
            return new MidiaRestritaDerivacaoService(storage, properties, processor, repository);
        }
    }

    /** One buffered timeline for the three-page, loopback-only capacity scenario. */
    private static final class ListTimeline implements R2VerificacaoAgrupadaPreviews.ListDiagnostic {
        private final AtomicReference<Run> current = new AtomicReference<>();

        void begin(String scenario) {
            assertThat(current.compareAndSet(null, new Run(scenario))).isTrue();
        }
        @Override public boolean enabled() { return current.get() != null; }
        @Override public void mark(String operationId, int page, String phase, long nanoTime, int value) {
            Run run = current.get();
            if (run != null) run.mark(operationId, page, phase, nanoTime, value);
        }
        void server(int page, String phase, int value) {
            Run run = current.get();
            if (run != null) run.mark(null, page, phase, System.nanoTime(), value);
        }
        void serverAt(int page, String phase, long nanoTime, int value) {
            Run run = current.get();
            if (run != null) run.mark(null, page, phase, nanoTime, value);
        }
        void operationDone() {
            Run run = current.get();
            if (run != null) run.done();
        }
        void report(Throwable failure) {
            Run run = current.getAndSet(null);
            if (run == null) return;
            try { System.out.println(run.line(failure)); }
            catch (RuntimeException | Error diagnosticFailure) {
                // Diagnostic formatting must never replace the functional test failure.
                System.out.println("CAPACITY_R2_TIMELINE result=DIAGNOSTIC_ERROR");
            }
        }

        private static final class Run {
            private final String scenario;
            private final long[] gcBefore = gcTotals();
            private final long started = System.nanoTime();
            private long[] gcAfter;
            private String operationId = "none";
            private int maxPage;
            private final Map<String, Long> times = new HashMap<>();
            private final Map<String, Integer> values = new HashMap<>();

            private Run(String scenario) { this.scenario = scenario; }
            private synchronized void mark(String id, int page, String phase, long at, int value) {
                if (id != null) operationId = id;
                maxPage = Math.max(maxPage, page);
                String key = page + "." + phase;
                times.putIfAbsent(key, at);
                values.put(key, value);
            }
            private void done() {
                mark(null, 0, "operation_done", System.nanoTime(), 0);
                gcAfter = gcTotals();
            }
            private synchronized String line(Throwable failure) {
                StringBuilder out = new StringBuilder("CAPACITY_R2_TIMELINE scenario=").append(scenario)
                        .append(" result=").append(failure == null ? "PASS" : failure.getClass().getSimpleName())
                        .append(" operation=").append(operationId)
                        .append(" setupMs=").append(offset(0, "list_start"))
                        .append(" requested=").append(value(0, "list_start"))
                        .append(" elapsedMs=").append(offset(0, "operation_done"));
                long[] after = gcAfter == null ? gcTotals() : gcAfter;
                out.append(" gcCount=").append(after[0] - gcBefore[0])
                        .append(" gcTimeMs=").append(after[1] - gcBefore[1]);
                for (int page = 1; page <= Math.max(3, maxPage); page++) {
                    out.append(" p").append(page).append("[at=").append(offset(page, "page_start"))
                            .append(",prep=").append(delta(page, "page_start", "send_start"))
                            .append(",dispatch=").append(delta(page, "send_start", "server_enter"))
                            .append(",serverPrep=").append(delta(page, "server_enter", "sleep_start"))
                            .append(",sleep=").append(delta(page, "sleep_start", "sleep_done"))
                            .append(",xml=").append(delta(page, "xml_start", "xml_done"))
                            .append(",write=").append(delta(page, "xml_done", "write_done"))
                            .append(",httpWait=").append(delta(page, "send_start", "headers"))
                            .append(",firstByte=").append(delta(page, "headers", "body_first"))
                            .append(",transfer=").append(delta(page, "body_first", "body_received"))
                            .append(",materialize=").append(delta(page, "body_received", "future_done"))
                            .append(",postHttp=").append(delta(page, "future_done", "parse_start"))
                            .append(",parse=").append(delta(page, "parse_start", "parse_done"))
                            .append(",match=").append(delta(page, "match_start", "match_done"))
                            .append(",next=").append(page < maxPage ? between(page, "match_done", page + 1, "send_start") : "-")
                            .append(",bytes=").append(value(page, "parse_start"))
                            .append(",keys=").append(value(page, "parse_done"))
                            .append(",found=").append(value(page, "match_done")).append(']');
                }
                return out.toString();
            }
            private String value(int page, String phase) {
                Integer value = values.get(page + "." + phase);
                return value == null ? "-" : value.toString();
            }
            private String offset(int page, String phase) {
                Long at = times.get(page + "." + phase);
                return at == null ? "-" : ms(at - started);
            }
            private String delta(int page, String from, String to) {
                return between(page, from, page, to);
            }
            private String between(int fromPage, String from, int toPage, String to) {
                Long start = times.get(fromPage + "." + from);
                Long end = times.get(toPage + "." + to);
                return start == null || end == null ? "-" : ms(end - start);
            }
            private static String ms(long nanos) {
                return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000d);
            }
            private static long[] gcTotals() {
                long count = 0, time = 0;
                for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                    count += Math.max(0, collector.getCollectionCount());
                    time += Math.max(0, collector.getCollectionTime());
                }
                return new long[]{count, time};
            }
        }
    }

    static final class LocalS3 {
        final HttpServer server;
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), runnable -> { Thread thread = new Thread(runnable, "capacity-local-s3"); thread.setDaemon(true); return thread; });
        final AtomicInteger heads = new AtomicInteger(), lists = new AtomicInteger(), active = new AtomicInteger(), maxActive = new AtomicInteger(), jdbcWhileWaiting = new AtomicInteger(), mutations = new AtomicInteger();
        final AtomicLong bytes = new AtomicLong();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile HikariDataSource pool;
        volatile List<String> objects = List.of();
        volatile long[] pageDelays = {1250, 850};
        volatile long headDelayMs;
        volatile String fault = "";
        volatile int gatePage;
        volatile CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);

        private LocalS3(HttpServer server) { this.server = server; server.setExecutor(executor); server.createContext("/", this::handle); server.start(); }
        static LocalS3 start() { try { return new LocalS3(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16)); } catch (IOException error) { throw new ExceptionInInitializerError(error); } }
        void reset() {
            release.countDown(); assertThat(active.get()).isZero();
            heads.set(0); lists.set(0); maxActive.set(0); bytes.set(0); jdbcWhileWaiting.set(0); mutations.set(0); failure.set(null);
            objects = List.of(); fault = ""; gatePage = 0; headDelayMs = 0;
            entered = new CountDownLatch(1); release = new CountDownLatch(1);
        }
        void configure(Set<String> requested, int total, long... delays) {
            assertThat(active.get()).isZero();
            // Lexical extras before target keys force the relevant search through the final page.
            TreeSet<String> inventory = new TreeSet<>(requested);
            for (int i = 0; inventory.size() < total; i++) inventory.add(PREFIX + "000-extra-" + String.format(Locale.ROOT, "%06d", i) + ".jpg");
            objects = List.copyOf(inventory);
            pageDelays = delays.clone(); gatePage = 0; fault = "";
            entered = new CountDownLatch(1); release = new CountDownLatch(1);
        }
        private void handle(HttpExchange exchange) throws IOException {
            long enteredAt = TIMELINE.enabled() ? System.nanoTime() : 0L;
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("AWS4-HMAC-SHA256 ");
                assertThat(exchange.getRequestHeaders().getFirst("x-amz-date")).isNotBlank();
                if (exchange.getRequestMethod().equals("HEAD")) {
                    heads.incrementAndGet(); samplePool();
                    Thread.sleep(headDelayMs); samplePool();
                    String key = exchange.getRequestURI().getPath().substring("/publicas-teste/".length());
                    exchange.sendResponseHeaders(objects.contains(key) ? 200 : 404, -1);
                    return;
                }
                if (!exchange.getRequestMethod().equals("GET")) { mutations.incrementAndGet(); throw new AssertionError("write method forbidden"); }
                assertThat(exchange.getRequestURI().getPath()).isIn("/publicas-teste", "/publicas-teste/");
                Map<String, String> query = new HashMap<>();
                for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                    String[] parts = pair.split("=", 2);
                    query.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
                }
                assertThat(query).containsEntry("list-type", "2").containsEntry("prefix", PREFIX).containsEntry("max-keys", "1000");
                String continuationCursor = query.get("continuation-token");
                int page = continuationCursor == null ? 1 : Integer.parseInt(continuationCursor.substring("synthetic-page-".length()));
                if (enteredAt != 0L) TIMELINE.serverAt(page, "server_enter", enteredAt, 0);
                lists.incrementAndGet(); samplePool();
                if (page == gatePage) { entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                TIMELINE.server(page, "sleep_start", 0);
                Thread.sleep(pageDelays[Math.min(page - 1, pageDelays.length - 1)]); samplePool();
                TIMELINE.server(page, "sleep_done", 0);
                int responseStatus = fault.matches("[45][0-9][0-9]") ? Integer.parseInt(fault) : 200;
                TIMELINE.server(page, "xml_start", 0);
                byte[] response = xml(page).getBytes(StandardCharsets.UTF_8);
                TIMELINE.server(page, "xml_done", response.length);
                bytes.addAndGet(response.length);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(responseStatus, response.length);
                TIMELINE.server(page, "headers_sent", responseStatus);
                try { exchange.getResponseBody().write(response); } catch (IOException cancelledClient) { /* Expected on explicit deadline/cancellation. */ }
                TIMELINE.server(page, "write_done", response.length);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                try { exchange.sendResponseHeaders(500, -1); } catch (IOException alreadyClosed) { /* diagnostic retained above */ }
            } finally { exchange.close(); active.decrementAndGet(); }
        }
        private String xml(int page) {
            if (fault.equals("malformed")) return "<ListBucketResult><Contents>";
            List<String> responseObjects = objects;
            if (fault.equals("incomplete") || fault.equals("repeated_token"))
                responseObjects = objects.subList(0, objects.size() - 1); // Keep one required proof unresolved.
            int from = Math.min((page - 1) * 1000, responseObjects.size()), to = Math.min(page * 1000, responseObjects.size());
            boolean truncated = to < responseObjects.size() || fault.equals("incomplete") || fault.equals("repeated_token");
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
            xml.append("<Name>publicas-teste</Name><Prefix>").append(encode(PREFIX)).append("</Prefix><EncodingType>url</EncodingType><MaxKeys>1000</MaxKeys><KeyCount>")
                    .append(to - from).append("</KeyCount><IsTruncated>").append(truncated).append("</IsTruncated>");
            if (page > 1) xml.append("<ContinuationToken>synthetic-page-").append(page).append("</ContinuationToken>");
            if (truncated && !fault.equals("missing_token")) xml.append("<NextContinuationToken>synthetic-page-")
                    .append(fault.equals("repeated_token") ? 2 : page + 1).append("</NextContinuationToken>");
            for (int i = from; i < to; i++) xml.append("<Contents><Key>").append(encode(responseObjects.get(i)))
                    .append("</Key><LastModified>2026-09-05T00:00:00.000Z</LastModified><ETag>&quot;0123456789abcdef0123456789abcdef&quot;</ETag><Size>1024</Size><StorageClass>STANDARD</StorageClass></Contents>");
            // Match at least the measured XML volume (295653 + 106254 bytes), not a tiny toy listing.
            int minimum = page == 1 ? 295653 : (to - from >= 1000 ? 295653 : 106254);
            int padding = minimum - xml.toString().getBytes(StandardCharsets.UTF_8).length - "</ListBucketResult>".length();
            if (padding > 0) xml.append(" ".repeat(padding));
            return xml.append("</ListBucketResult>").toString();
        }
        private void samplePool() { if (pool != null) jdbcWhileWaiting.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max); }
        private static String encode(String input) { return URLEncoder.encode(input, StandardCharsets.UTF_8).replace("+", "%20"); }
        private static String decode(String input) { return URLDecoder.decode(input, StandardCharsets.UTF_8); }
        void close() throws InterruptedException { release.countDown(); server.stop(0); executor.shutdownNow(); assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }
}
