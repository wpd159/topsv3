package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumExpiracaoPolicyService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.LocalizacaoPublicaDto;
import br.com.topsdojob.v3.application.publico.mapper.MidiaPublicaMapper;
import br.com.topsdojob.v3.application.publico.premium.PremiumPublicoMapper;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioLocalizacaoRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.BairroRepository;
import br.com.topsdojob.v3.persistence.repository.CidadeRepository;
import br.com.topsdojob.v3.persistence.repository.EstadoRepository;
import br.com.topsdojob.v3.platform.health.HealthController;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** PostgreSQL/JPA/Hikari and the real locality/eligibility/mapper pipeline; only storage is controlled. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = TopsDoJobBackendApplication.class,
        initializers = LocalidadesConsultaPostgres17IntegrationTest.PostgresInitializer.class)
@Import({LocalidadePublicaConsultaService.class, LocalidadesConsultaCoordenador.class,
        AnuncioSeoElegibilidadeConsultaService.class, AnuncioSeoIndexabilidadePolicy.class,
        LocalidadeSeoIndexabilidadePolicy.class, MidiaPublicaMapper.class,
        MidiaPublicaUrlService.class,
        PremiumPublicoMapper.class, BeneficioAnuncioConsultaService.class,
        PremiumExpiracaoPolicyService.class, FotoUploadProcessor.class,
        LocalidadesConsultaPostgres17IntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "LOCALIDADES_POSTGRES17_ENABLED", matches = "true")
@TestMethodOrder(OrderAnnotation.class)
class LocalidadesConsultaPostgres17IntegrationTest {
    private static final Duration LIMITE_OBSERVACAO = Duration.ofSeconds(5);
    private static final long LIMITE_TOTAL_MS = 4_000; // 3500 ms budget + at most 500 ms scheduling/cleanup.
    private static final UUID GO = UUID.fromString("71000000-0000-4000-8000-000000000001");
    private static final UUID SP = UUID.fromString("71000000-0000-4000-8000-000000000002");
    private static final UUID CIDADE_GO = UUID.fromString("72000000-0000-4000-8000-000000000001");
    private static final UUID CIDADE_SP = UUID.fromString("72000000-0000-4000-8000-000000000002");

    @Autowired private LocalidadePublicaConsultaService service;
    @SpyBean private LocalidadesConsultaCoordenador coordenador;
    @Autowired private HikariDataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ControlledStorage storage;
    @Autowired private AnuncioRepository anuncios;
    @Autowired private AnuncioLocalizacaoRepository locais;
    @Autowired private EstadoRepository estados;
    @Autowired private CidadeRepository cidades;
    @Autowired private BairroRepository bairros;
    @Autowired private AnuncioSeoElegibilidadeConsultaService elegibilidade;
    @PersistenceContext private EntityManager em;
    private final List<ExecutorService> executores = new ArrayList<>();
    private final EvaluationBoundary evaluation = new EvaluationBoundary();

    @BeforeEach
    void preparar() {
        esperarTerminoReal();
        storage.reset();
        evaluation.reset();
        reset(coordenador);
        // Test-only scheduling boundary: the producer entered coordination, but JDBC/snapshot
        // have not started. Release always invokes the real final transaction and evaluation.
        doAnswer(call -> {
            evaluation.beforeSnapshot();
            return call.callRealMethod();
        }).when(coordenador).transacao(anyString(), any());
        jdbc.execute("truncate usuario, estado, arquivo_midia cascade");
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(30_000);
        assertThat(jdbc.queryForObject("show server_version_num", Integer.class))
                .isBetween(170000, 179999);
    }

    @AfterEach
    void liberarRecursosDoCenario() throws Exception {
        storage.release();
        evaluation.release();
        for (ExecutorService executor : executores) {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        esperarTerminoReal();
        assertPoolLivre();
    }

    @AfterAll
    static void encerrarBancoDescartavel() throws Exception {
        PostgresSupport.stop();
    }

    @Test
    @Order(1)
    void coldNaturalSemCacheEComFiltrosPublicosReais() {
        Fixture ids = fixture();
        jdbc.update("update anuncio set categoria='ACOMPANHANTE_MASCULINO' where id=?", ids.spDois());
        inserirAnuncio(GO, CIDADE_GO, "PAUSADO", "APROVADO", "ATIVO", false, false);
        inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "REJEITADO", "ATIVO", false, false);
        inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "APROVADO", "DESATIVADO", false, false);
        inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "APROVADO", "ATIVO", true, false);
        inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "APROVADO", "ATIVO", false, true);
        long antes = coordenador.produtoresIniciados();
        long sqlAntes = sqlPreparados();
        long inicio = System.nanoTime();
        assertDescoberta(service.descobrir(), 1, 2);
        assertThat(storage.calls.get()).isZero();
        assertThat(storage.dentroTransacao.get()).isZero();
        assertThat(sqlPreparados() - sqlAntes).isPositive();
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(1);
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        evidence("cold_primeira_sem_cache", inicio, antes);
        long inicioSegunda = System.nanoTime();
        assertDescoberta(service.descobrir(), 1, 2);
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(2);
        assertThat(storage.calls.get()).isZero(); // Fresh DB evaluation, not a completed eligibility cache.
        assertThat(elapsed(inicioSegunda)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        evidence("cold_segunda_sem_cache", inicioSegunda, antes + 1);
        AgregadoCidadePublicaDto paulista = service.agregadoCidade("SP", "central");
        assertThat(paulista.indexacao().anunciosElegiveisUnicos()).isEqualTo(2);
        assertThat(paulista.indexacao().indexavel()).isFalse();
        assertThat(paulista.categorias()).extracting(item -> item.codigo())
                .containsExactly("ACOMPANHANTE_FEMININA", "ACOMPANHANTE_MASCULINO");
        assertThat(paulista.categorias()).extracting(item -> item.totalAnunciosAtivos())
                .containsExactly(1L, 1L);
    }

    @Test
    void inventarioSinteticoDe103AnunciosExecutaDuasCargasNaturaisSemCache() {
        fixture();
        for (int i = 0; i < 100; i++)
            inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "APROVADO", "ATIVO", false, false);
        assertThat(jdbc.queryForObject("select count(*) from anuncio", Integer.class)).isEqualTo(103);
        for (int passagem = 1; passagem <= 2; passagem++) {
            long produtoresAntes = coordenador.produtoresIniciados();
            long sqlAntes = sqlPreparados();
            int headsAntes = storage.calls.get();
            long inicio = System.nanoTime();
            DescobertaLocalidadesPublicaDto resultado = service.descobrir();
            long duracao = elapsed(inicio);
            long quantidadeSql = sqlPreparados() - sqlAntes;
            assertDescoberta(resultado, 101, 2);
            assertThat(resultado.estados()).extracting(item -> item.uf()).containsExactly("GO", "SP");
            var go = resultado.estados().get(0);
            assertThat(go.indexacao().anunciosElegiveisUnicos()).isEqualTo(101);
            assertThat(go.indexacao().indexavel()).isTrue();
            assertThat(go.cidades()).singleElement().satisfies(cidade -> {
                assertThat(cidade.indexacao().anunciosElegiveisUnicos()).isEqualTo(101);
                assertThat(cidade.indexacao().indexavel()).isTrue();
            });
            assertThat(resultado.estados().get(1).indexacao().anunciosElegiveisUnicos()).isEqualTo(2);
            assertThat(resultado.estados().get(1).indexacao().indexavel()).isFalse();
            assertThat(duracao).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
            assertThat(quantidadeSql).isPositive();
            assertThat(coordenador.produtoresIniciados() - produtoresAntes).isEqualTo(1);
            assertThat(storage.calls.get() - headsAntes).isZero();
            assertThat(storage.dentroTransacao.get()).isZero();
            assertPoolLivre();
            System.out.printf("LOCALIDADES_EVIDENCIA inventario_sintetico=103 passagem=%d duracaoMs=%d sqlPreparados=%d produtoresDelta=1 heads=0 headDentroTx=0 poolAtivo=0 poolEspera=0 storage=NAO_CONSULTADO%n",
                    passagem, duracao, quantidadeSql);
        }
    }

    @Test
    void erroRemotoIrrelevanteNaoImpedeElegibilidadeComFotoLivre() {
        fixture();
        storage.failure = new IllegalStateException("indisponibilidade remota sintetica");
        long antes = coordenador.produtoresIniciados();
        long inicio = System.nanoTime();
        assertDescoberta(service.descobrir(), 1, 2);
        esperarTerminoReal();
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(storage.active.get()).isZero();
        assertThat(storage.calls.get()).isZero();
        assertThat(storage.dentroTransacao.get()).isZero();
        assertThat(atividade("state in ('active','idle in transaction')")).isZero();
        assertPoolLivre();
        evidence("erro_remoto_nao_consultado", inicio, antes);
        storage.failure = null;
        assertDescoberta(service.descobrir(), 1, 2);
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(2);
        assertTimeoutsGlobaisPreservados();
    }

    @Test
    void prazoInterrompeEsperaAntesDoSnapshotSemJdbcEEsperaTerminoReal() throws Exception {
        fixture();
        Gate gate = evaluation.block(false);
        long antes = coordenador.produtoresIniciados();
        long inicio = System.nanoTime();
        Future<Integer> futuro = executor(1).submit(() -> status(service::descobrir));
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolLivre();
        assertThat(atividade("state in ('active','idle in transaction')")).isZero();
        assertThat(futuro.get(4, TimeUnit.SECONDS)).isEqualTo(503);
        esperarTerminoReal();
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(gate.release.getCount()).isEqualTo(1); // Real worker interruption ends the controlled wait.
        assertThat(evaluation.interruptions.get()).isEqualTo(1);
        assertThat(evaluation.active.get()).isZero();
        assertThat(storage.calls.get()).isZero();
        assertThat(storage.dentroTransacao.get()).isZero();
        assertPoolLivre();
        evidence("timeout_entrada_snapshot_interruptivel", inicio, antes);
        gate.release.countDown();
        assertDescoberta(service.descobrir(), 1, 2);
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(2);
        assertTimeoutsGlobaisPreservados();
    }

    @Test
    void descobertaVaziaNaoConsultaStorageENaoInventouCidade() {
        assertThat(service.descobrir().estados()).isEmpty();
        assertThat(storage.calls.get()).isZero();
        assertThatThrownBy(() -> service.agregadoCidade("GO", "central"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void consumidoresMistosCompartilhamUmProdutorSemConexaoDuranteEsperaDoSnapshot(int quantidade) throws Exception {
        fixture();
        Gate gate = evaluation.block(false);
        long antes = coordenador.produtoresIniciados();
        long inicio = System.nanoTime();
        List<Future<Object>> futuros = consumidoresMistos(quantidade);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(coordenador.consumidoresAguardando()).isEqualTo(quantidade));
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(1);
        assertThat(coordenador.trabalhoRemanescente()).isEqualTo(1);
        assertPoolLivre();
        assertThat(storage.dentroTransacao.get()).isZero();
        assertThat(evaluation.active.get()).isEqualTo(1);
        assertThat(storage.calls.get()).isZero();
        evidence("concorrencia_" + quantidade + "_antes_snapshot", inicio, antes);
        gate.release.countDown();
        for (int i = 0; i < futuros.size(); i++) assertConsumidor(i, futuros.get(i).get(4, TimeUnit.SECONDS));
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(1);
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(storage.calls.get()).isZero();
        evidence("concorrencia_" + quantidade + "_final", inicio, antes);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 20})
    void baselineControladoDoPadraoAntigoOcupaAsCincoConexoes(int quantidade) throws Exception {
        fixture();
        // This is a harness of the former transaction scope, not the historical binary.
        // It calls the same real repositories/eligibility/mapper with HEAD inside a transaction.
        Gate gate = storage.block(5, false);
        ExecutorService executor = executor(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) futuros.add(executor.submit(() -> {
            largada.await();
            TransactionTemplate tx = new TransactionTemplate(manager);
            tx.setReadOnly(true);
            return tx.execute(status -> baselineRepositorioEMapper());
        }));
        long inicio = System.nanoTime();
        largada.countDown();
        assertThat(gate.entered.await(3, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(5);
            assertThat(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection())
                    .isEqualTo(quantidade - 5);
        });
        assertThat(storage.dentroTransacao.get()).isGreaterThanOrEqualTo(5);
        System.out.printf("LOCALIDADES_EVIDENCIA baseline_harness_antigo consumidores=%d poolAtivo=5 poolEspera=%d duracaoMs=%d%n",
                quantidade, quantidade - 5, elapsed(inicio));
        gate.release.countDown();
        for (Future<Integer> futuro : futuros) assertThat(futuro.get(5, TimeUnit.SECONDS)).isEqualTo(3);
    }

    @Test
    void cancelamentoDeUmConsumidorNaoCancelaOsDemais() throws Exception {
        fixture();
        Gate gate = evaluation.block(false);
        long antes = coordenador.produtoresIniciados();
        List<Future<Object>> futuros = consumidoresMistos(6);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(2)).until(() -> coordenador.consumidoresAguardando() == 6);
        assertThat(futuros.get(0).cancel(true)).isTrue();
        await().atMost(Duration.ofSeconds(1)).until(() -> coordenador.consumidoresAguardando() == 5);
        assertThat(coordenador.trabalhoRemanescente()).isEqualTo(1);
        gate.release.countDown();
        for (int i = 1; i < futuros.size(); i++) assertConsumidor(i, futuros.get(i).get(4, TimeUnit.SECONDS));
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(1);
    }

    @Test
    void retryAposPrazoNaoAcumulaWorkerEnquantoTrabalhoRealNaoTerminou() throws Exception {
        fixture();
        Gate gate = evaluation.block(true);
        long antes = coordenador.produtoresIniciados();
        long inicio = System.nanoTime();
        Future<Integer> primeiro = executor(1).submit(() -> status(service::descobrir));
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(primeiro.get(4, TimeUnit.SECONDS)).isEqualTo(503);
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(coordenador.possuiExecucaoEmVoo()).isTrue();
        assertThat(coordenador.trabalhoRemanescente()).isEqualTo(1);
        for (int i = 0; i < 20; i++) assertThat(status(service::descobrir)).isEqualTo(503);
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(1);
        assertPoolLivre();
        evidence("prazo_retries_20_worker_retido", inicio, antes);
        AtomicInteger aquisicoesReadiness = new AtomicInteger();
        DataSource readinessDataSource = new DelegatingDataSource(dataSource) {
            @Override public Connection getConnection() throws SQLException {
                aquisicoesReadiness.incrementAndGet();
                return super.getConnection();
            }
        };
        try (HealthFixture fixtureHealth = healthController(readinessDataSource)) {
            HealthController health = fixtureHealth.controller();
            var request = new MockHttpServletRequest("GET", "/api/health/readiness");
            long inicioReadiness = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                assertThat(health.readiness(request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            }
            assertThat(elapsed(inicioReadiness)).isLessThan(500);
            assertThat(health.liveness(request).status()).isEqualTo("UP");
            assertThat(aquisicoesReadiness.get()).isZero();
            assertPoolLivre();
            gate.release.countDown();
            esperarTerminoReal();
            await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(health.readiness(request).getStatusCode()).isEqualTo(HttpStatus.OK));
            assertThat(aquisicoesReadiness.get()).isPositive();
            System.out.printf("LOCALIDADES_EVIDENCIA readiness_worker_expirado chamadas=20 status=503 aquisicoesExtraEnquantoExpirado=0 recuperacao=200%n");
        }
        assertDescoberta(service.descobrir(), 1, 2);
        assertThat(coordenador.produtoresIniciados() - antes).isEqualTo(2);
    }

    @Test
    void snapshotFinalExcluiRemocaoEModeracaoConfirmadasAntesDaEntrada() throws Exception {
        Fixture ids = fixture();
        assertDescoberta(service.descobrir(), 1, 2);
        Gate gate = evaluation.block(false);
        Future<DescobertaLocalidadesPublicaDto> futuro = executor(1).submit(service::descobrir);
        assertThat(gate.entered.await(2, TimeUnit.SECONDS)).isTrue();
        assertPoolLivre();
        jdbc.update("update anuncio set removido_em=now(), atualizado_em=now() where id=?", ids.go());
        jdbc.update("update anuncio set status_moderacao='REJEITADO', atualizado_em=now() where id=?", ids.spUm());
        gate.release.countDown();
        assertDescoberta(futuro.get(4, TimeUnit.SECONDS), 0, 1);
        assertThat(storage.calls.get()).isZero();
        assertThatThrownBy(() -> service.agregadoCidade("GO", "central"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void lockSqlRealEncerraDentroDoPrazoELiberaTrabalhoAntesDaProximaCarga() throws Exception {
        fixture();
        long antes = coordenador.produtoresIniciados();
        try (Connection bloqueador = PostgresSupport.observer()) {
            bloqueador.setAutoCommit(false);
            try (Statement lock = bloqueador.createStatement()) {
                lock.execute("lock table anuncio in access exclusive mode");
            }
            long inicio = System.nanoTime();
            Future<Integer> futuro = executor(1).submit(() -> status(service::descobrir));
            await().atMost(Duration.ofSeconds(2)).until(() -> atividade("wait_event_type='Lock'") == 1);
            assertThat(futuro.get(4, TimeUnit.SECONDS)).isEqualTo(503);
            esperarTerminoReal();
            assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
            assertThat(atividade("state in ('active','idle in transaction')")).isZero();
            assertPoolLivre();
            evidence("lock_sql_cancelado", inicio, antes);
            bloqueador.rollback();
        }
        assertDescoberta(service.descobrir(), 1, 2);
        assertTimeoutsGlobaisPreservados();
    }

    @Test
    void aquisicaoComPoolEsgotadoEhInterrompidaSemAguardarTrintaSegundos() throws Exception {
        List<Connection> ocupadas = new ArrayList<>();
        long antes = coordenador.produtoresIniciados();
        try {
            for (int i = 0; i < 5; i++) ocupadas.add(dataSource.getConnection());
            long inicio = System.nanoTime();
            Future<Integer> futuro = executor(1).submit(() -> status(service::descobrir));
            await().atMost(Duration.ofSeconds(2)).until(() ->
                    dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection() == 1);
            assertThat(futuro.get(4, TimeUnit.SECONDS)).isEqualTo(503);
            esperarTerminoReal();
            assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
            assertThat(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(5);
            assertThat(atividade("state in ('active','idle in transaction')")).isZero();
            evidence("aquisicao_pool_esgotado", inicio, antes);
        } finally {
            for (Connection connection : ocupadas) connection.close();
        }
        assertThat(service.descobrir().estados()).isEmpty();
        assertTimeoutsGlobaisPreservados();
    }

    @Test
    void readinessRealDetectaPoolSaturadoEmUmSegundoSemAfetarLivenessERecupera() throws Exception {
        List<Connection> ocupadas = new ArrayList<>();
        var request = new MockHttpServletRequest("GET", "/api/health/readiness");
        try (HealthFixture fixtureHealth = healthController()) {
            HealthController health = fixtureHealth.controller();
            try {
                for (int i = 0; i < 5; i++) ocupadas.add(dataSource.getConnection());
                long inicio = System.nanoTime();
                assertThat(health.readiness(request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                long duracao = elapsed(inicio);
                // The internal limit is 1000 ms; permit only scheduling overhead on this host.
                assertThat(duracao).isBetween(900L, 1_200L);
                assertThat(health.liveness(request).status()).isEqualTo("UP");
                assertThat(health.health(request).status()).isEqualTo("UP");
                await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofMillis(500))
                        .until(() -> dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection() == 0);
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(5);
                assertThat(atividade("state in ('active','idle in transaction')")).isZero();
                System.out.printf("LOCALIDADES_EVIDENCIA readiness_pool_saturado status=503 duracaoMs=%d poolAtivo=5 poolEspera=0 liveness=UP%n", duracao);
            } finally {
                for (Connection connection : ocupadas) connection.close();
            }
            await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                    assertThat(health.readiness(request).getStatusCode()).isEqualTo(HttpStatus.OK));
            assertPoolLivre();
            assertTimeoutsGlobaisPreservados();
        }
    }

    @Test
    void consultasSequenciaisCompartilhamPrazoEPostgresEncerraSessaoReal() {
        AtomicInteger pid = new AtomicInteger();
        long antes = coordenador.produtoresIniciados();
        long inicio = System.nanoTime();
        int resultado = status(() -> coordenador.executar("sql_sequencial", () ->
                coordenador.transacao("sql_sequencial", () -> {
                    pid.set(((Number) em.createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue());
                    em.createNativeQuery("select 1 from pg_sleep(2)").getSingleResult();
                    return em.createNativeQuery("select 1 from pg_sleep(2)").getSingleResult();
                })));
        assertThat(resultado).isEqualTo(503);
        esperarTerminoReal();
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(pid.get()).isPositive();
        await().atMost(Duration.ofMillis(500)).until(() -> observerScalar(
                "select count(*) from pg_stat_activity where pid=" + pid.get()) == 0);
        assertPoolLivre();
        evidence("sql_sequencial_sessao_encerrada", inicio, antes);
        assertThat(coordenador.executar("recuperacao", () -> coordenador.transacao("recuperacao",
                () -> ((Number) em.createNativeQuery("select 1").getSingleResult()).intValue())))
                .isEqualTo(1);
        assertTimeoutsGlobaisPreservados();
    }

    @Test
    void segundaTransacaoRecebeSomenteSaldoENaoReiniciaOrcamento() {
        AtomicLong segundoSaldo = new AtomicLong();
        long inicio = System.nanoTime();
        int resultado = status(() -> coordenador.executar("duas_transacoes", () -> {
            coordenador.transacao("primeira", () ->
                    em.createNativeQuery("select 1 from pg_sleep(2.2)").getSingleResult());
            return coordenador.transacao("segunda", () -> {
                segundoSaldo.set(((Number) em.createNativeQuery("""
                        select extract(epoch from current_setting('transaction_timeout')::interval)*1000
                        """).getSingleResult()).longValue());
                return em.createNativeQuery("select 1 from pg_sleep(2)").getSingleResult();
            });
        }));
        assertThat(resultado).isEqualTo(503);
        esperarTerminoReal();
        assertThat(segundoSaldo.get()).isBetween(1L, 1300L);
        assertThat(elapsed(inicio)).isLessThanOrEqualTo(LIMITE_TOTAL_MS);
        assertThat(atividade("state in ('active','idle in transaction')")).isZero();
        assertTimeoutsGlobaisPreservados();
        System.out.printf("LOCALIDADES_EVIDENCIA duas_transacoes saldoSegundaMs=%d totalMs=%d trabalhoRestante=%d%n",
                segundoSaldo.get(), elapsed(inicio), coordenador.trabalhoRemanescente());
    }

    private int baselineRepositorioEMapper() {
        var ads = anuncios.findPublicosComProprietarioAtivo();
        var localizacoes = locais.findByAnuncioIdIn(ads.stream().map(ad -> ad.getId()).toList());
        estados.findAllById(localizacoes.stream().map(local -> local.getEstadoId()).toList());
        cidades.findAllById(localizacoes.stream().map(local -> local.getCidadeId()).toList());
        bairros.findAll();
        Map<UUID, LocalizacaoPublicaDto> publicas = new LinkedHashMap<>();
        localizacoes.forEach(local -> publicas.put(local.getAnuncioId(), new LocalizacaoPublicaDto(
                GO.equals(local.getEstadoId()) ? "GO" : "SP", "Estado sintetico",
                "Cidade sintetica", "central", null, null, null)));
        elegibilidade.avaliar(ads, publicas);
        // Negative control of the former transaction scope only. Public persisted
        // eligibility no longer performs remote I/O; this explicit controlled call
        // demonstrates why waiting on storage while holding JDBC saturates pool5.
        storage.exists(StorageArea.PUBLIC_MEDIA, "hml/publicas/restritas-borradas/v1/baseline-fixture.jpg");
        return ads.size();
    }

    private List<Future<Object>> consumidoresMistos(int quantidade) {
        ExecutorService executor = executor(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            int indice = i;
            futuros.add(executor.submit(() -> {
                largada.await();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                return switch (indice % 3) {
                    case 0 -> service.descobrir();
                    case 1 -> service.agregadoCidade("GO", "central");
                    default -> service.agregadoCidade("SP", "central");
                };
            }));
        }
        largada.countDown();
        return futuros;
    }

    private void assertConsumidor(int indice, Object valor) {
        if (indice % 3 == 0) {
            assertDescoberta((DescobertaLocalidadesPublicaDto) valor, 1, 2);
        } else {
            AgregadoCidadePublicaDto cidade = (AgregadoCidadePublicaDto) valor;
            assertThat(cidade.estadoUf()).isEqualTo(indice % 3 == 1 ? "GO" : "SP");
            assertThat(cidade.cidadeSlug()).isEqualTo("central");
            assertThat(cidade.totalAnunciosAtivos()).isEqualTo(indice % 3 == 1 ? 1 : 2);
        }
    }

    private void assertDescoberta(DescobertaLocalidadesPublicaDto resultado, long go, long sp) {
        assertThat(resultado.estados().stream().mapToLong(item -> item.totalAnunciosAtivos()).sum())
                .isEqualTo(go + sp);
        assertThat(resultado.estados().stream().filter(item -> item.uf().equals("GO"))
                .mapToLong(item -> item.totalAnunciosAtivos()).sum()).isEqualTo(go);
        assertThat(resultado.estados().stream().filter(item -> item.uf().equals("SP"))
                .mapToLong(item -> item.totalAnunciosAtivos()).sum()).isEqualTo(sp);
    }

    private Fixture fixture() {
        jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values (?,'GO','Goias','goias',now()),(?,'SP','Sao Paulo','sao paulo',now())", GO, SP);
        jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values (?,?,'Central Goias','central goias','central',now()),(?,?,'Central Paulista','central paulista','central',now())", CIDADE_GO, GO, CIDADE_SP, SP);
        UUID go = inserirAnuncio(GO, CIDADE_GO, "PUBLICADO", "APROVADO", "ATIVO", false, false);
        UUID spUm = inserirAnuncio(SP, CIDADE_SP, "PUBLICADO", "APROVADO", "ATIVO", false, false);
        UUID spDois = inserirAnuncio(SP, CIDADE_SP, "PUBLICADO", "APROVADO", "ATIVO", false, false);
        return new Fixture(go, spUm, spDois);
    }

    private UUID inserirAnuncio(UUID estado, UUID cidade, String statusAnuncio, String moderacao,
            String statusUsuario, boolean virtual, boolean removido) {
        UUID usuario = UUID.randomUUID();
        UUID anuncio = UUID.randomUUID();
        jdbc.update("insert into usuario(id,nome,status,tipo_conta,criado_em,atualizado_em,versao) values (?,'Pessoa sintetica',?,'ANUNCIANTE',now(),now(),0)", usuario, statusUsuario);
        jdbc.update("""
                insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,
                    atendimento_exclusivamente_virtual,publicado_em,criado_em,atualizado_em,versao,removido_em)
                values (?,?,?,'Companhia sintetica local',?, ?,?,'ACOMPANHANTE_FEMININA',?,now(),now(),now(),0,
                    case when ? then now() else null end)
                """, anuncio, usuario, "localidade-" + anuncio, "Descricao ficticia segura para validar localidades. ".repeat(4),
                statusAnuncio, moderacao, virtual, removido);
        jdbc.update("insert into anuncio_localizacao(anuncio_id,estado_id,cidade_id,criado_em,atualizado_em) values (?,?,?,now(),now())", anuncio, estado, cidade);
        for (int ordem = 0; ordem < 2; ordem++) {
            UUID arquivo = UUID.randomUUID();
            boolean restrita = ordem == 0;
            jdbc.update("""
                    insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                    values (?,'R2',?,?,'image/jpeg',1024,'VALIDADO',now())
                    """, arquivo, restrita ? "privadas-teste" : "publicas-teste",
                    (restrita ? "hml/privadas/" : "hml/publicas/") + arquivo + ".jpg");
            jdbc.update("""
                    insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,
                        visibilidade_midia,criado_em,atualizado_em)
                    values (?,?,?,'FOTO','GALERIA',?,'PUBLICAVEL',?,now(),now())
                    """, UUID.randomUUID(), anuncio, arquivo, ordem, restrita ? "RESTRITA_18" : "LIVRE");
        }
        return anuncio;
    }

    private void assertTimeoutsGlobaisPreservados() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(30_000);
        for (String parametro : List.of("statement_timeout", "lock_timeout", "transaction_timeout"))
            assertThat(jdbc.queryForObject("show " + parametro, String.class)).isEqualTo("0");
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getNetworkTimeout()).isZero();
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private void esperarTerminoReal() {
        await().pollInterval(Duration.ofMillis(10)).atMost(LIMITE_OBSERVACAO).untilAsserted(() -> {
            assertThat(coordenador.possuiExecucaoEmVoo()).isFalse();
            assertThat(coordenador.trabalhoRemanescente()).isZero();
            assertThat(coordenador.consumidoresAguardando()).isZero();
        });
    }

    private void assertPoolLivre() {
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }

    private long atividade(String predicado) {
        return observerScalar("select count(*) from pg_stat_activity where application_name='localidades-it' and " + predicado);
    }

    private long observerScalar(String sql) {
        try (Connection connection = PostgresSupport.observer(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new AssertionError("consulta observadora local falhou", exception);
        }
    }

    private long sqlPreparados() {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
    }

    private ExecutorService executor(int quantidade) {
        ExecutorService executor = Executors.newFixedThreadPool(quantidade);
        executores.add(executor);
        return executor;
    }

    private HealthFixture healthController() throws Exception {
        return healthController(dataSource);
    }

    private HealthFixture healthController(DataSource source) throws Exception {
        var context = new AnnotationConfigApplicationContext();
        // Borrow these instances without registering disposal callbacks: closing
        // the isolated health context must not close the fixture pool/coordinator.
        context.getBeanFactory().registerSingleton("dataSource", source);
        context.getBeanFactory().registerSingleton("coordenador", coordenador);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("health-test",
                Map.of("app.health.readiness-timeout-ms", "1500", "app.health.readiness-query-timeout-seconds", "1")));
        String healthPackage = "br.com.topsdojob.v3.platform.health.";
        context.register(HealthController.class,
                Class.forName(healthPackage + "BackendReadinessService"),
                Class.forName(healthPackage + "ApplicationReadinessState"),
                Class.forName(healthPackage + "DatabaseReadinessProbe"),
                Class.forName(healthPackage + "PackagedMigrationCatalog"));
        try {
            context.refresh();
            context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(
                    new org.springframework.boot.SpringApplication(HealthController.class),
                    new String[0], context, Duration.ZERO));
            return new HealthFixture(context.getBean(HealthController.class), context);
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
    }

    private record HealthFixture(HealthController controller, AnnotationConfigApplicationContext context)
            implements AutoCloseable {
        @Override public void close() { context.close(); }
    }

    private int status(Supplier<?> trabalho) {
        try { trabalho.get(); return 200; }
        catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
    }

    private static long elapsed(long inicio) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio); }

    private void evidence(String cenario, long inicio, long antes) {
        var pool = dataSource.getHikariPoolMXBean();
        System.out.printf("LOCALIDADES_EVIDENCIA cenario=%s duracaoMs=%d produtoresDelta=%d consumidores=%d trabalhoRestante=%d poolAtivo=%d poolOcioso=%d poolEspera=%d heads=%d headDentroTx=%d%n",
                cenario, elapsed(inicio), coordenador.produtoresIniciados() - antes,
                coordenador.consumidoresAguardando(), coordenador.trabalhoRemanescente(),
                pool.getActiveConnections(), pool.getIdleConnections(), pool.getThreadsAwaitingConnection(),
                storage.calls.get(), storage.dentroTransacao.get());
    }

    private record Fixture(UUID go, UUID spUm, UUID spDois) { }

    @TestConfiguration
    @EnableConfigurationProperties({R2StorageProperties.class, MidiaUploadProperties.class})
    static class TestBeans {
        @Bean ControlledStorage controlledStorage() { return new ControlledStorage(); }
        @Bean MidiaRestritaDerivacaoService derivacaoControlada(ObjectProvider<ObjectStorage> provider,
                R2StorageProperties properties, FotoUploadProcessor processor, ArquivoMidiaRepository repository) {
            return new MidiaRestritaDerivacaoService(provider, properties, processor, repository);
        }
    }

    static final class Gate {
        final CountDownLatch entered;
        final CountDownLatch release = new CountDownLatch(1);
        final boolean ignoreInterrupt;
        Gate(int entradas, boolean ignoreInterrupt) {
            this.entered = new CountDownLatch(entradas);
            this.ignoreInterrupt = ignoreInterrupt;
        }
    }

    static final class EvaluationBoundary {
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger interruptions = new AtomicInteger();
        volatile Gate gate;

        Gate block(boolean ignoreInterrupt) { return gate = new Gate(1, ignoreInterrupt); }
        void release() { if (gate != null) gate.release.countDown(); }
        void reset() { release(); gate = null; active.set(0); interruptions.set(0); }
        void beforeSnapshot() {
            Gate current = gate;
            if (current == null) return;
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            active.incrementAndGet();
            boolean interrupted = false;
            try {
                current.entered.countDown();
                while (true) {
                    try {
                        if (!current.release.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("entrada transacional controlada nao liberada");
                        return;
                    } catch (InterruptedException error) {
                        interrupted = true;
                        interruptions.incrementAndGet();
                        if (!current.ignoreInterrupt)
                            throw new IllegalStateException("entrada transacional controlada interrompida", error);
                    }
                }
            } finally {
                active.decrementAndGet();
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    static final class ControlledStorage implements ObjectStorage {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxActive = new AtomicInteger();
        final AtomicInteger dentroTransacao = new AtomicInteger();
        final AtomicInteger interruptions = new AtomicInteger();
        volatile Gate gate;
        volatile RuntimeException failure;

        Gate block(int entradas, boolean ignoreInterrupt) { return gate = new Gate(entradas, ignoreInterrupt); }
        void release() { if (gate != null) gate.release.countDown(); }
        void reset() { release(); gate = null; failure = null; calls.set(0); active.set(0); maxActive.set(0); dentroTransacao.set(0); interruptions.set(0); }
        @Override public boolean exists(StorageArea area, String key) {
            assertThat(area).isEqualTo(StorageArea.PUBLIC_MEDIA);
            assertThat(key).contains("restritas-borradas/v1/");
            calls.incrementAndGet();
            if (TransactionSynchronizationManager.isActualTransactionActive()) dentroTransacao.incrementAndGet();
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            boolean interrompida = false;
            try {
                if (failure != null) throw failure;
                Gate controle = gate;
                if (controle != null) {
                    controle.entered.countDown();
                    while (true) {
                        try {
                            if (!controle.release.await(10, TimeUnit.SECONDS)) throw new AssertionError("gate local nao liberado");
                            break;
                        } catch (InterruptedException exception) {
                            interrompida = true;
                            interruptions.incrementAndGet();
                            if (!controle.ignoreInterrupt) throw new IllegalStateException("HEAD controlado interrompido", exception);
                        }
                    }
                }
                return true;
            } finally {
                active.decrementAndGet();
                if (interrompida) Thread.currentThread().interrupt();
            }
        }
        @Override public Optional<URI> publicUrl(StorageArea area, String key) { return Optional.of(URI.create("https://public.example.invalid/" + key)); }
        @Override public void put(StorageArea area, String key, byte[] content, String type) { throw new AssertionError("PUT fora do escopo readonly"); }
        @Override public ObjectWriteResult putIfAbsent(StorageArea area, String key, byte[] content, String type) { throw new AssertionError("PUT fora do escopo readonly"); }
        @Override public StoredObject get(StorageArea area, String key) { throw new AssertionError("GET de midia nao esperado"); }
        @Override public void delete(StorageArea area, String key) { throw new AssertionError("DELETE fora do escopo readonly"); }
        @Override public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) { throw new AssertionError("URL temporaria nao esperada"); }
    }

    static final class PostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            try {
                PostgresSupport.start();
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("localidades-postgres17",
                        Map.ofEntries(
                                Map.entry("spring.datasource.url", PostgresSupport.url() + "?ApplicationName=localidades-it"),
                                Map.entry("spring.datasource.username", "topsv3test"),
                                Map.entry("spring.datasource.password", PostgresSupport.CREDENTIAL),
                                Map.entry("spring.datasource.hikari.maximum-pool-size", "5"),
                                Map.entry("spring.datasource.hikari.minimum-idle", "5"),
                                Map.entry("spring.datasource.hikari.connection-timeout", "30000"),
                                Map.entry("spring.flyway.enabled", "false"),
                                Map.entry("spring.jpa.hibernate.ddl-auto", "validate"),
                                Map.entry("spring.jpa.properties.hibernate.generate_statistics", "true"),
                                Map.entry("logging.level.org.hibernate.stat", "OFF"),
                                Map.entry("logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener", "OFF"),
                                Map.entry("app.storage.r2.enabled", "true"),
                                Map.entry("app.storage.r2.public-media-bucket", "publicas-teste"),
                                Map.entry("app.storage.r2.private-media-bucket", "privadas-teste"),
                                Map.entry("app.storage.r2.public-media-prefix", "hml/publicas/"),
                                Map.entry("app.storage.r2.private-media-prefix", "hml/privadas/"))));
            } catch (Exception exception) { throw new IllegalStateException("PostgreSQL17 local de localidades indisponivel", exception); }
        }
    }

    static final class PostgresSupport {
        private static final String OWNER_ID = UUID.randomUUID().toString().replace("-", "");
        private static final String NETWORK = "topsv3-localidades-" + OWNER_ID + "-net";
        private static final String CONTAINER = "topsv3-localidades-" + OWNER_ID + "-pg17";
        private static final String CREDENTIAL = UUID.randomUUID().toString() + UUID.randomUUID();
        private static int port;
        private static boolean started;

        static synchronized void start() throws Exception {
            if (started) return;
            try {
                command(Map.of(), "docker", "network", "create", "--label", "topsv3.localidades.owner=" + OWNER_ID, NETWORK);
                command(Map.of("POSTGRES_PASSWORD", CREDENTIAL), "docker", "run", "--pull=never", "-d",
                        "--name", CONTAINER, "--label", "topsv3.localidades.owner=" + OWNER_ID,
                        "--network", NETWORK, "-p", "127.0.0.1::5432", "-e", "POSTGRES_DB=topsv3_localidades",
                        "-e", "POSTGRES_USER=topsv3test", "-e", "POSTGRES_PASSWORD", "postgres:17.10-alpine");
                String mapping = command(Map.of(), "docker", "port", CONTAINER, "5432/tcp").trim();
                port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));
                long limite = System.nanoTime() + Duration.ofSeconds(90).toNanos();
                SQLException ultimo = null;
                boolean pronto = false;
                while (System.nanoTime() < limite) {
                    try (Connection connection = observer(); Statement statement = connection.createStatement();
                            ResultSet result = statement.executeQuery("select 1")) {
                        pronto = result.next() && result.getInt(1) == 1;
                        if (pronto) break;
                    } catch (SQLException exception) { ultimo = exception; }
                    Thread.sleep(100);
                }
                if (!pronto) throw new IllegalStateException("PostgreSQL17 nao respondeu SQL TCP autenticado", ultimo);
                Path migrations = Path.of("src", "main", "resources", "db", "migration").toAbsolutePath().normalize();
                command(Map.of("FLYWAY_PASSWORD", CREDENTIAL), "docker", "run", "--pull=never", "--rm",
                        "--network", NETWORK, "-e", "FLYWAY_PASSWORD", "-v", migrations + ":/flyway/sql:ro",
                        "flyway/flyway:12.10.0", "-url=jdbc:postgresql://" + CONTAINER + ":5432/topsv3_localidades",
                        "-user=topsv3test", "-locations=filesystem:/flyway/sql", "migrate");
                started = true;
            } catch (Exception exception) {
                try { stop(); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        }

        static String url() { return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_localidades"; }
        static Connection observer() throws SQLException {
            return DriverManager.getConnection(url() + "?ApplicationName=localidades-observer&connectTimeout=2&socketTimeout=2", "topsv3test", CREDENTIAL);
        }

        static synchronized void stop() throws Exception {
            Exception falha = null;
            try { removeOwned("container", CONTAINER); } catch (Exception exception) { falha = exception; }
            try { removeOwned("network", NETWORK); } catch (Exception exception) {
                if (falha == null) falha = exception; else falha.addSuppressed(exception);
            }
            started = false;
            if (falha != null) throw falha;
        }

        private static void removeOwned(String tipo, String nome) throws Exception {
            String template = tipo.equals("container")
                    ? "{{json .Config.Labels}}"
                    : "{{json .Labels}}";
            Result inspecao = run(Map.of(), "docker", tipo, "inspect", "--format", template, nome);
            if (inspecao.exit != 0) {
                if (inspecao.output.toLowerCase().contains("no such")) return;
                throw new IllegalStateException("falha na inspecao do recurso local " + nome + ": " + inspecao.output);
            }
            String owner = new com.fasterxml.jackson.databind.ObjectMapper().readTree(inspecao.output)
                    .path("topsv3.localidades.owner").asText();
            if (!OWNER_ID.equals(owner)) throw new IllegalStateException("ownership local divergente: " + nome);
            if (tipo.equals("container")) command(Map.of(), "docker", "container", "rm", "-f", "-v", nome);
            else command(Map.of(), "docker", "network", "rm", nome);
        }

        private static String command(Map<String, String> environment, String... args) throws Exception {
            Result result = run(environment, args);
            if (result.exit != 0) throw new IllegalStateException("comando de fixture local falhou: " + result.output);
            return result.output;
        }

        private static Result run(Map<String, String> environment, String... args) throws Exception {
            Path log = Files.createTempFile("localidades-pg17-", ".log");
            try {
                ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile());
                builder.environment().putAll(environment);
                Process process = builder.start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                    throw new IllegalStateException("prazo do comando local excedido");
                }
                return new Result(process.exitValue(), Files.readString(log, StandardCharsets.UTF_8));
            } finally { Files.deleteIfExists(log); }
        }

        private record Result(int exit, String output) { }
    }
}
