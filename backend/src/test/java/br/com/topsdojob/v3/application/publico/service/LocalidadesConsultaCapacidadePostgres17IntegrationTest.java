package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumExpiracaoPolicyService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageConfiguration;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2VerificacaoAgrupadaPreviews;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.stream.Collectors;
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

/** Real PG17/JPA/pool5 and signed R2 HTTP; only the local S3 server's data/time are synthetic. */
@DataJpaTest(showSql = false)
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
    @Autowired private LocalidadePublicaConsultaService service;
    @Autowired private LocalidadesConsultaCoordenador coordinator;
    @Autowired private HikariDataSource pool;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MidiaRestritaDerivacaoService derivation;
    @Autowired private R2VerificacaoAgrupadaPreviews verifier;
    @Autowired private R2StorageProperties properties;
    @Autowired private ObjectStorage objectStorage;
    @SpyBean private AnuncioSeoElegibilidadeConsultaService eligibility;
    @PersistenceContext private EntityManager em;
    private final List<ExecutorService> executors = new ArrayList<>();
    private final AtomicReference<Map<UUID, AnuncioSeoElegibilidadeConsultaService.Resultado>> evaluated = new AtomicReference<>();
    private final AtomicReference<Set<String>> verifiedKeys = new AtomicReference<>();

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
        reset(verifier, eligibility);
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
        }).when(eligibility).avaliar(any(), any());
        evaluated.set(null);
        jdbc.execute("truncate usuario, estado, arquivo_midia cascade");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getConnectionTimeout()).isEqualTo(30_000);
        assertThat(jdbc.queryForObject("show server_version_num", Integer.class)).isBetween(170000, 179999);
    }

    @AfterEach
    void cleanup() throws Exception {
        REMOTE.release.countDown();
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
        finally { LocalidadesConsultaPostgres17IntegrationTest.PostgresSupport.stop(); }
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
            assertThat(verifiedKeys.get()).containsExactlyInAnyOrderElementsOf(fixture.keys());
            assertThat(duration).isLessThan(3500.0); // Functional deadline: NO cleanup tolerance.
            assertThat(metrics().transactions - before.transactions).isEqualTo(2);
            assertThat(REMOTE.lists.get() - before.lists).isEqualTo(2);
            assertThat(REMOTE.heads.get() - before.heads).isZero();
            assertThat(REMOTE.bytes.get() - before.bytes).isGreaterThanOrEqualTo(401_907);
            assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
            assertThat(REMOTE.maxActive.get()).isEqualTo(1);
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
    void terceiraPaginaComObjetosNaoPertinentesAindaRespeitaOrcamento() {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 2358, 1250, 800, 800);
        Metrics before = metrics();
        long started = System.nanoTime();
        DescobertaLocalidadesPublicaDto response = service.descobrir();
        double duration = milliseconds(started);
        assertComplete(response, fixture.ads());
        assertThat(duration).isLessThan(3500.0);
        assertThat(REMOTE.lists.get()).isEqualTo(3);
        assertThat(REMOTE.bytes.get()).isGreaterThanOrEqualTo(690_000);
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("three_pages_with_unrelated_objects", started, duration, before);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void consumidoresGlobaisECidadesCompartilhamListagemReal(int count) throws Exception {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.gatePage = 1;
        Metrics before = metrics();
        long started = System.nanoTime();
        List<Future<Object>> consumers = consumers(count);
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == count);
        assertThat(coordinator.produtoresIniciados() - before.producers).isEqualTo(1);
        assertPoolFree();
        assertThat(observerCount("state in ('active','idle in transaction')")).isZero();
        REMOTE.release.countDown();
        for (int i = 0; i < count; i++) assertConsumer(i, consumers.get(i).get(3500, TimeUnit.MILLISECONDS), fixture.ads());
        double duration = milliseconds(started);
        assertThat(duration).isLessThan(3500.0);
        assertThat(REMOTE.lists.get()).isEqualTo(2);
        assertThat(REMOTE.maxActive.get()).isEqualTo(1);
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("consumers_" + count, started, duration, before);
    }

    @Test
    void headSerialRealDe240msContinuaSendoReferenciaReprovada() {
        Inventory fixture = seed(715, 975);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.headDelayMs = 240;
        // Only this regression restores the former serial algorithm, using real signed HTTP HEADs.
        doAnswer(call -> {
            Set<String> requested = call.getArgument(0);
            Set<String> found = new LinkedHashSet<>();
            for (String key : requested) if (objectStorage.exists(StorageArea.PUBLIC_MEDIA, key)) found.add(key);
            return Set.copyOf(found);
        }).when(verifier).verificar(anySet());
        Metrics before = metrics();
        long started = System.nanoTime();
        assertThat(status(service::descobrir)).isEqualTo(503);
        double duration = milliseconds(started);
        drained();
        assertThat(duration).isBetween(3400.0, 4000.0);
        assertThat(REMOTE.heads.get()).isBetween(1, 16);
        assertThat(REMOTE.heads.get()).isLessThan(975);
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(metrics().transactions - before.transactions).isEqualTo(1);
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        evidence("serial_head_240ms_expected_503", started, duration, before);
        reset(verifier);
        REMOTE.headDelayMs = 0;
        await().atMost(Duration.ofSeconds(1)).until(() -> REMOTE.active.get() == 0);
        assertComplete(service.descobrir(), fixture.ads());
    }

    @Test
    void ausenciasExatasEDuplicatasNaoViraramCacheOuCorrespondenciaParcial() {
        Inventory fixture = seed(3, 3);
        UUID absentAd = fixture.ads().get(0);
        String absent = fixture.keyByAd().get(absentAd);
        Set<String> present = new LinkedHashSet<>(fixture.keys());
        present.remove(absent);
        present.add(PREFIX + absent.substring(PREFIX.length()).toUpperCase(Locale.ROOT));
        present.add(absent + ".original");
        present.add(PREFIX + "original-" + absent.substring(PREFIX.length()));
        REMOTE.configure(present, 1358, 1250, 850);
        // Restricted previews do not become authorized originals; the existing free photo
        // keeps the ad eligible according to the unchanged real SEO policy.
        assertComplete(service.descobrir(), fixture.ads());
        assertThat(verifiedKeys.get()).doesNotContain(absent).hasSize(2);
        assertThat(REMOTE.lists.get()).isEqualTo(2); // Absence requires exhaustion, not a partial page.
        assertThat(REMOTE.heads.get()).isZero();
        // The first ad now uses exactly the second ad's preview; dedup stays operation-local.
        jdbc.update("update anuncio_midia set arquivo_midia_id=? where anuncio_id=? and ordem=0",
                fixture.fileByAd().get(fixture.ads().get(1)), absentAd);
        REMOTE.configure(new HashSet<>(fixture.keys()).stream().filter(key -> !key.equals(absent)).collect(Collectors.toSet()),
                1358, 1250, 850);
        assertComplete(service.descobrir(), fixture.ads());
        @SuppressWarnings("unchecked")
        Set<String> lastRequested = (Set<String>) org.mockito.Mockito.mockingDetails(verifier).getInvocations()
                .stream().filter(call -> call.getMethod().getName().equals("verificar")).reduce((a, b) -> b)
                .orElseThrow().getArgument(0);
        assertThat(lastRequested).hasSize(2);
        // A completed positive proof is not reused after the remote object disappears.
        REMOTE.configure(Set.of(), 1358, 1250, 850);
        assertComplete(service.descobrir(), fixture.ads());
        assertThat(verifiedKeys.get()).isEmpty();
    }

    @Test
    void catalogoLegitimamenteVazioNaoExecutaStorage() {
        assertThat(service.descobrir().estados()).isEmpty();
        assertThat(REMOTE.lists.get()).isZero();
        assertThat(REMOTE.heads.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"403", "429", "500", "malformed", "missing_token", "repeated_token", "incomplete"})
    void erroDeInventarioNuncaProduzCatalogoVazioEProximaOperacaoRecupera(String fault) {
        Inventory fixture = seed(3, 3);
        REMOTE.configure(fixture.keys(), 1358, 120, 120);
        REMOTE.fault = fault;
        long started = System.nanoTime();
        assertThat(status(service::descobrir)).isEqualTo(503);
        assertThat(milliseconds(started)).isLessThan(3500.0);
        drained();
        assertPoolFree();
        assertThat(REMOTE.heads.get()).isZero();
        assertThat(REMOTE.lists.get()).isBetween(1, 8);
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        int failedCalls = REMOTE.lists.get();
        REMOTE.fault = "";
        assertComplete(service.descobrir(), fixture.ads());
        assertThat(REMOTE.lists.get() - failedCalls).isEqualTo(2);
        System.out.printf("CAPACITY_NEGATIVE fault=%s status=503 recovery=complete listsFailed=%d heads=0%n", fault, failedCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"publication", "owner", "moderation", "removal", "media", "file", "identity", "identity_listed_extra"})
    void releituraFinalPreservaElegibilidadeDurantePaginacao(String change) throws Exception {
        Inventory fixture = seed(3, 3);
        UUID ad = fixture.ads().get(0);
        UUID file = fixture.fileByAd().get(ad);
        Set<String> inventory = new LinkedHashSet<>(fixture.keys());
        if (change.equals("identity_listed_extra")) {
            inventory.add(previewKey(file, "a".repeat(64)));
        }
        REMOTE.configure(inventory, 1358, 120, 120);
        REMOTE.gatePage = 2;
        Future<Integer> result = executor(1).submit(() -> status(service::descobrir));
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolFree();
        // A separate observer connection mutates only synthetic rows; it never consumes the app pool.
        switch (change) {
            case "publication" -> observerUpdate("update anuncio set status='PAUSADO' where id=?", ad);
            case "owner" -> observerUpdate("update usuario set status='DESATIVADO' where id=(select usuario_id from anuncio where id=?)", ad);
            case "moderation" -> observerUpdate("update anuncio set status_moderacao='REJEITADO' where id=?", ad);
            case "removal" -> observerUpdate("update anuncio set removido_em=now() where id=?", ad);
            case "media" -> observerUpdate("update anuncio_midia set status='REJEITADA' where anuncio_id=? and ordem=1", ad);
            case "file" -> observerUpdate("update arquivo_midia set status_arquivo='REMOVIDO' where id=(select arquivo_midia_id from anuncio_midia where anuncio_id=? and ordem=1)", ad);
            default -> observerUpdate("update arquivo_midia set sha256=? where id=?", "a".repeat(64), file);
        }
        REMOTE.release.countDown();
        if (change.startsWith("identity")) assertThat(result.get(3500, TimeUnit.MILLISECONDS)).isEqualTo(503);
        else {
            assertThat(result.get(3500, TimeUnit.MILLISECONDS)).isEqualTo(200);
            assertEligibleIds(fixture.ads().subList(1, 3));
        }
        assertThat(REMOTE.jdbcWhileWaiting.get()).isZero();
        assertThat(REMOTE.heads.get()).isZero();
    }

    @Test
    void mudancaAposUltimaPaginaAntesDaReleituraNaoHerdaProva() {
        Inventory fixture = seed(3, 3);
        REMOTE.configure(fixture.keys(), 1358, 120, 120);
        doAnswer(call -> {
            Object verified = call.callRealMethod();
            observerUpdate("update arquivo_midia set sha256=? where id=?", "b".repeat(64), fixture.fileByAd().get(fixture.ads().get(0)));
            return verified;
        }).when(verifier).verificar(anySet());
        assertThat(status(service::descobrir)).isEqualTo(503);
        assertThat(REMOTE.lists.get()).isEqualTo(2);
        assertThat(REMOTE.heads.get()).isZero();
    }

    @Test
    void timeoutHttpRealNaoMantemProdutorOuJdbcERecupera() throws Exception {
        Inventory fixture = seed(715, 1311);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.gatePage = 2;
        Metrics before = metrics();
        long started = System.nanoTime();
        Future<Integer> response = executor(1).submit(() -> status(service::descobrir));
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolFree();
        assertThat(response.get(4, TimeUnit.SECONDS)).isEqualTo(503);
        double duration = milliseconds(started);
        assertThat(duration).isLessThanOrEqualTo(4000.0);
        drained();
        // HttpClient cancellation ends local work, not the deliberately held remote handler.
        assertThat(REMOTE.active.get()).isEqualTo(1);
        assertPoolFree();
        REMOTE.release.countDown();
        await().atMost(Duration.ofSeconds(2)).until(() -> REMOTE.active.get() == 0);
        evidence("timeout_second_page", started, duration, before);
        REMOTE.gatePage = 0;
        assertComplete(service.descobrir(), fixture.ads());
    }

    @Test
    void desistenciasNaoAbremListagensPorConsumidorEClienteSeguinteRecupera() throws Exception {
        Inventory fixture = seed(715, 975);
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.gatePage = 1;
        long producers = coordinator.produtoresIniciados();
        List<Future<Object>> consumers = consumers(6);
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 6);
        assertThat(consumers.get(0).cancel(true)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 5);
        assertThat(coordinator.trabalhoRemanescente()).isEqualTo(1);
        REMOTE.release.countDown();
        for (int i = 1; i < 6; i++) assertConsumer(i, consumers.get(i).get(3500, TimeUnit.MILLISECONDS), fixture.ads());
        assertThat(coordinator.produtoresIniciados() - producers).isEqualTo(1);
        assertThat(REMOTE.lists.get()).isEqualTo(2);
        drained();
        REMOTE.configure(fixture.keys(), 1358, 1250, 850);
        REMOTE.gatePage = 1;
        consumers = consumers(6);
        assertThat(REMOTE.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofMillis(500)).until(() -> coordinator.consumidoresAguardando() == 6);
        for (Future<Object> consumer : consumers) assertThat(consumer.cancel(true)).isTrue();
        drained();
        assertPoolFree();
        REMOTE.release.countDown();
        await().atMost(Duration.ofSeconds(2)).until(() -> REMOTE.active.get() == 0);
        REMOTE.gatePage = 0;
        assertComplete(service.descobrir(), fixture.ads());
        assertThat(REMOTE.maxActive.get()).isEqualTo(1);
    }

    private Inventory seed(int ads, int previews) {
        jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values (?,'GO','Goias','goias',now()),(?,'SP','Sao Paulo','sao paulo',now())", GO, SP);
        jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values (?,?,'Central Goias','central goias','central',now()),(?,?,'Central Paulista','central paulista','central',now())", CITY_GO, GO, CITY_SP, SP);
        List<UUID> ids = new ArrayList<>();
        Map<UUID, String> keyByAd = new LinkedHashMap<>();
        Map<UUID, UUID> fileByAd = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < ads; i++) {
            UUID user = UUID.randomUUID(), ad = UUID.randomUUID();
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
        UUID file = UUID.randomUUID();
        jdbc.update("""
                insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                values (?,'R2',?,?,'image/jpeg',1024,'VALIDADO',now())
                """, file, restricted ? "privadas-teste" : "publicas-teste", (restricted ? "hml/privadas/" : "hml/publicas/") + file + ".jpg");
        jdbc.update("""
                insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,visibilidade_midia,criado_em,atualizado_em)
                values (?,?,?,'FOTO','GALERIA',?,'PUBLICAVEL',?,now(),now())
                """, UUID.randomUUID(), ad, file, order, restricted ? "RESTRITA_18" : "LIVRE");
        return file;
    }

    private String previewKey(UUID id, String checksum) {
        return derivation.chavePublica(ArquivoMidiaEntity.criarUploadPendente(id, "R2", "privadas-teste",
                "hml/privadas/" + id + ".jpg", null, "image/jpeg", 1024L, null, null, null,
                checksum, OffsetDateTime.now()));
    }

    private void assertComplete(DescobertaLocalidadesPublicaDto result, List<UUID> expected) {
        assertThat(result.estados().stream().mapToLong(state -> state.totalAnunciosAtivos()).sum()).isEqualTo(expected.size());
        assertThat(result.estados().stream().mapToLong(state -> state.indexacao().anunciosElegiveisUnicos()).sum()).isEqualTo(expected.size());
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
            // Default production constructor: lazy HttpClient creation is inside the first measured discovery.
            return spy(new R2VerificacaoAgrupadaPreviews(properties));
        }
        @Bean MidiaRestritaDerivacaoService derivation(ObjectProvider<ObjectStorage> storage,
                R2StorageProperties properties, FotoUploadProcessor processor, R2VerificacaoAgrupadaPreviews verifier) {
            return new MidiaRestritaDerivacaoService(storage, properties, processor, verifier);
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
                String token = query.get("continuation-token");
                int page = token == null ? 1 : Integer.parseInt(token.substring("synthetic-page-".length()));
                lists.incrementAndGet(); samplePool();
                if (page == gatePage) { entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                Thread.sleep(pageDelays[Math.min(page - 1, pageDelays.length - 1)]); samplePool();
                int responseStatus = fault.matches("[45][0-9][0-9]") ? Integer.parseInt(fault) : 200;
                byte[] response = xml(page).getBytes(StandardCharsets.UTF_8);
                bytes.addAndGet(response.length);
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(responseStatus, response.length);
                try { exchange.getResponseBody().write(response); } catch (IOException cancelledClient) { /* Expected on explicit deadline/cancellation. */ }
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
