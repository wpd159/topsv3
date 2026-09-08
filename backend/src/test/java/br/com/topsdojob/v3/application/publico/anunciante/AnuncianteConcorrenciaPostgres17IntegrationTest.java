package br.com.topsdojob.v3.application.publico.anunciante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator;
import br.com.topsdojob.v3.application.anuncio.AnuncioAtualizacaoCanonicaValidator.DadosAtualizacao;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.LimiteMidiasAnuncioService;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import br.com.topsdojob.v3.application.publico.kyc.KycPublicoService;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.persistence.entity.anuncio.AnuncioEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.AnuncioRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.env=homologacao",
                "app.event.hash-salt=hash-fixture",
                "app.age-gate.signing-value=age-gate-runtime-test-value",
                "app.outbox.email.enabled=false",
                "app.storage.r2.enabled=true",
                "app.storage.r2.endpoint=https://127.0.0.1:1",
                "app.storage.r2.access-key=EXEMPLO_NAO_REAL",
                "app.storage.r2.signing-value=EXEMPLO_NAO_REAL",
                "app.storage.r2.private-media-bucket=privadas",
                "app.storage.r2.public-media-bucket=publicas",
                "app.storage.r2.private-media-prefix=hml/midias-pendentes/",
                "app.storage.r2.public-media-prefix=hml/midias-aprovadas/",
                "efi.pix.enabled=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.task.scheduling.enabled=false",
                "spring.datasource.hikari.maximum-pool-size=12"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = AnuncianteConcorrenciaPostgres17IntegrationTest.FixtureTestContextListener.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@EnabledIfEnvironmentVariable(named = "ANUNCIANTE_CONCURRENCY_POSTGRES17_ENABLED", matches = "true")
class AnuncianteConcorrenciaPostgres17IntegrationTest {

    private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ANUNCIO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ESTADO_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CIDADE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String SLUG = "anuncio-concorrencia-qa";
    private static final Postgres17Fixture POSTGRES = Postgres17Fixture.start();

    @Autowired
    private MinhasMidiasService midiasService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AnuncioRepository anuncioRepository;

    @Autowired
    private AnuncioMidiaRepository anuncioMidiaRepository;

    @Autowired
    private ArquivoMidiaRepository arquivoMidiaRepository;

    @MockBean
    private MeusAnunciosConsultaService consultaService;

    @MockBean
    private KycPublicoService kycService;

    @MockBean
    private AnuncioAtualizacaoCanonicaValidator atualizacaoValidator;

    @MockBean
    private LimiteMidiasAnuncioService limiteService;

    @MockBean
    private MidiaUploadValidator uploadValidator;

    @MockBean
    private FotoUploadProcessor fotoProcessor;

    @MockBean
    private ObjectStorage objectStorage;

    private final Map<String, StoredObject> objetos = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::username);
        registry.add("spring.datasource.password", POSTGRES::credential);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE usuario, estado CASCADE");
        objetos.clear();

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        usuarioRepository.saveAndFlush(UsuarioEntity.criarSolicitacaoLocal(
                USUARIO_ID,
                "Usuario QA",
                "usuario.qa@example.invalid",
                null,
                agora));
        anuncioRepository.saveAndFlush(AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID,
                USUARIO_ID,
                SLUG,
                "Anuncio inicial QA",
                "Descricao inicial sintetica para concorrencia",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                agora));
    }

    private static ConfigurableApplicationContext fixtureContext;
    private static TestContext fixtureTestContext;
    private static Set<Thread> threadsAntesDosTestes;

    public static final class FixtureTestContextListener extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return Integer.MIN_VALUE;
        }

        @Override
        public void beforeTestClass(TestContext testContext) {
            // Captura a identidade existente; nao inicializa nem resolve ApplicationContext.
            fixtureTestContext = testContext;
        }
    }

    @Autowired
    void registrarContextoDaFixture(ConfigurableApplicationContext context) {
        synchronized (AnuncianteConcorrenciaPostgres17IntegrationTest.class) {
            if (fixtureContext != null && fixtureContext != context) {
                throw new IllegalStateException("contexto da fixture mudou durante a classe");
            }
            fixtureContext = context;
        }
    }

    @BeforeEach
    void registrarConsumidoresDaFixture() {
        synchronized (AnuncianteConcorrenciaPostgres17IntegrationTest.class) {
            if (threadsAntesDosTestes == null) {
                threadsAntesDosTestes = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Thread thread : Thread.getAllStackTraces().keySet()) {
                    if (thread.isAlive() && !thread.isDaemon()) threadsAntesDosTestes.add(thread);
                }
            }
        }
    }

    @AfterAll
    static void encerrarFixtureDepoisDosConsumidores() {
        // Sem resolucao de parametro Spring: falha ao criar o contexto nao pode impedir cleanup.
        aguardarConsumidoresDaFixture();
        if (threadsAntesDosTestes == null) {
            System.out.println("ANUNCIANTE_FIXTURE_CONSUMERS baseline_absent=true test_bodies_not_started=true owner="
                    + POSTGRES.owner);
        } else {
            System.out.println("ANUNCIANTE_FIXTURE_CONSUMERS new_non_daemon_alive=0 owner=" + POSTGRES.owner);
        }
        ConfigurableApplicationContext context = fixtureContext;
        if (context != null) {
            TestContext testContext = fixtureTestContext;
            if (testContext == null
                    || testContext.getTestClass() != AnuncianteConcorrenciaPostgres17IntegrationTest.class
                    || !testContext.hasApplicationContext()) {
                throw new IllegalStateException("TestContext da fixture nao esta disponivel para encerramento");
            }
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            if (!POSTGRES.jdbcUrl().equals(dataSource.getJdbcUrl())) {
                throw new IllegalStateException("datasource nao pertence a esta fixture");
            }
            int active = dataSource.getHikariPoolMXBean().getActiveConnections();
            int waiting = dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
            if (active != 0 || waiting != 0) {
                throw new IllegalStateException("JDBC da fixture ainda possui consumidores");
            }
            System.out.println("ANUNCIANTE_FIXTURE_HIKARI active=" + active + " waiting=" + waiting
                    + " owner=" + POSTGRES.owner);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread closing = new Thread(() -> {
                try {
                    // Remove a entrada e fecha o contexto antes dos listeners afterTestClass.
                    testContext.markApplicationContextDirty(DirtiesContext.HierarchyMode.CURRENT_LEVEL);
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            }, "anunciante-fixture-context-close");
            closing.setDaemon(true);
            closing.start();
            aguardarThread(closing, System.nanoTime() + TimeUnit.SECONDS.toNanos(20));
            if (failure.get() != null) {
                throw new IllegalStateException("falha ao fechar contexto da fixture", failure.get());
            }
            if (context.isActive() || !dataSource.isClosed()) {
                throw new IllegalStateException("contexto ou pool da fixture nao encerrou");
            }
            aguardarConsumidoresDaFixture();
            System.out.println("ANUNCIANTE_FIXTURE_CONTEXT closed=true pool_closed=true owner=" + POSTGRES.owner);
        }
        POSTGRES.close();
    }

    private static void aguardarConsumidoresDaFixture() {
        if (threadsAntesDosTestes == null) return;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            List<Thread> remaining = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.isAlive() && !thread.isDaemon())
                    .filter(thread -> !threadsAntesDosTestes.contains(thread))
                    .toList();
            if (remaining.isEmpty()) return;
            for (Thread thread : remaining) aguardarThread(thread, deadline);
        }
    }

    private static void aguardarThread(Thread thread, long deadline) {
        while (thread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new IllegalStateException("consumidor nao encerrou: thread-id=" + thread.getId());
            }
            try {
                thread.join(Math.max(1, Math.min(200, TimeUnit.NANOSECONDS.toMillis(remaining))));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("encerramento da fixture interrompido", exception);
            }
        }
    }

    @Test
    void cincoUploadsConcorrentesRespeitamLimiteOrdemEIdempotenciaSemErroInterno()
            throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(consultaService.anuncioDoUsuario(eq(SLUG), eq(authentication)))
                .thenAnswer(ignored -> anuncioFixture("Anuncio inicial QA"));
        when(uploadValidator.validar(any())).thenReturn(uploadValidado());
        when(fotoProcessor.processar(any())).thenReturn(fotoProcessada());

        CyclicBarrier leiturasConcorrentes = new CyclicBarrier(5);
        AtomicInteger resolucoes = new AtomicInteger();
        when(limiteService.resolver(ANUNCIO_ID)).thenAnswer(ignored -> {
            if (resolucoes.incrementAndGet() <= 5) {
                aguardarBarreira(leiturasConcorrentes);
            }
            return new LimiteMidiasAnuncioService.Resultado(4, 0, false, false);
        });
        when(objectStorage.putIfAbsent(eq(StorageArea.PRIVATE_MEDIA), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(1);
                    StoredObject novo = new StoredObject(invocation.getArgument(2), invocation.getArgument(3));
                    return objetos.putIfAbsent(key, novo) == null
                            ? ObjectWriteResult.CREATED
                            : ObjectWriteResult.ALREADY_EXISTS;
                });
        when(objectStorage.get(eq(StorageArea.PRIVATE_MEDIA), anyString()))
                .thenAnswer(invocation -> objetos.get(invocation.getArgument(1)));
        when(objectStorage.temporaryGetUrl(eq(StorageArea.PRIVATE_MEDIA), anyString(), any()))
                .thenReturn(URI.create("https://private.invalid/temporary"));
        org.mockito.Mockito.doAnswer(invocation -> objetos.remove(invocation.getArgument(1)))
                .when(objectStorage).delete(eq(StorageArea.PRIVATE_MEDIA), anyString());

        var executor = Executors.newFixedThreadPool(5);
        List<Callable<UploadResultado>> tarefas = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            String chave = "upload-concorrente-" + index;
            tarefas.add(() -> executarUpload(chave, authentication));
        }

        List<UploadResultado> resultados;
        try {
            resultados = executor.invokeAll(tarefas).stream()
                    .map(future -> obter(future, 20))
                    .toList();
        } finally {
            executor.shutdownNow();
        }

        assertThat(resultados)
                .withFailMessage("resultados concorrentes: %s", resultados)
                .allSatisfy(resultado -> assertThat(resultado.status()).isIn(200, 409));
        assertThat(resultados).filteredOn(resultado -> resultado.status() == 200).hasSize(4);
        assertThat(resultados).filteredOn(resultado -> resultado.status() == 409).hasSize(1);
        assertThat(anuncioMidiaRepository.findByAnuncioId(ANUNCIO_ID))
                .hasSize(4)
                .extracting(item -> item.getOrdem())
                .doesNotHaveDuplicates();
        assertThat(arquivoMidiaRepository.count()).isEqualTo(4L);
        assertThat(objetos).hasSize(4);

        String chaveConfirmada = resultados.stream()
                .filter(resultado -> resultado.status() == 200)
                .findFirst()
                .orElseThrow()
                .chave();
        assertThat(executarUpload(chaveConfirmada, authentication).status()).isEqualTo(200);
        assertThat(anuncioMidiaRepository.findByAnuncioId(ANUNCIO_ID)).hasSize(4);
        assertThat(arquivoMidiaRepository.count()).isEqualTo(4L);
        assertThat(objetos).hasSize(4);
    }

    @Test
    void cincoEdicoesConcorrentesRetornamSucessoOuConflitoComEstadoIntegro() throws Exception {
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(USUARIO_ID);
        when(usuario.getTelefoneNormalizado()).thenReturn(null);
        when(consultaService.usuarioAutenticado(nullable(Authentication.class))).thenReturn(usuario);
        when(consultaService.anuncioDoUsuario(eq(SLUG), nullable(Authentication.class)))
                .thenAnswer(ignored -> anuncioFixture("Anuncio inicial QA"));
        when(consultaService.detalhar(eq(SLUG), nullable(Authentication.class))).thenReturn(null);

        CyclicBarrier validacoesConcorrentes = new CyclicBarrier(5);
        AtomicInteger validacoes = new AtomicInteger();
        when(atualizacaoValidator.validar(any(), nullable(String.class))).thenAnswer(invocation -> {
            if (validacoes.incrementAndGet() <= 5) {
                aguardarBarreira(validacoesConcorrentes);
            }
            var request = invocation.getArgument(
                    0,
                    br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto.class);
            return new DadosAtualizacao(
                    request.titulo(),
                    "Descricao valida sintetica para a edicao concorrente",
                    "ACOMPANHANTE_FEMININA",
                    new BigDecimal("150.00"),
                    "GO",
                    "Cidade QA",
                    null,
                    null,
                    Set.of(),
                    Set.of(),
                    false,
                    null,
                    null);
        });
        when(atualizacaoValidator.slugify("Cidade QA")).thenReturn("cidade-qa");
        when(atualizacaoValidator.textoBusca(any(DadosAtualizacao.class), nullable(String.class)))
                .thenReturn("documento de busca qa");
        seedLocalidade();

        var executor = Executors.newFixedThreadPool(5);
        List<Callable<MvcResult>> tarefas = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            int tentativa = index;
            tarefas.add(() -> mockMvc.perform(patch("/api/public/minha-conta/anuncios/{slug}", SLUG)
                            .with(csrf())
                            .header("X-Request-Id", "request-ad-concurrency-" + tentativa)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadEdicao("Anuncio concorrente " + tentativa)))
                    .andReturn());
        }

        List<MvcResult> resultados;
        try {
            resultados = executor.invokeAll(tarefas).stream()
                    .map(future -> obter(future, 20))
                    .toList();
        } finally {
            executor.shutdownNow();
        }

        assertThat(resultados)
                .extracting(resultado -> resultado.getResponse().getStatus())
                .allMatch(status -> status == 200 || status == 409)
                .contains(200, 409)
                .doesNotContain(500);
        assertThat(resultados).filteredOn(resultado -> resultado.getResponse().getStatus() == 200)
                .hasSize(1);
        assertThat(resultados).filteredOn(resultado -> resultado.getResponse().getStatus() == 409)
                .hasSize(4)
                .allSatisfy(resultado -> {
                    assertThat(resultado.getResponse().getHeader("Cache-Control")).contains("no-store");
                    assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8))
                            .contains("O anúncio foi alterado por outra operação")
                            .contains("request-ad-concurrency-")
                            .doesNotContain("stackTrace");
                });

        Map<String, Object> estadoFinal = jdbc.queryForMap(
                "SELECT usuario_id, titulo, versao FROM anuncio WHERE id = ?",
                ANUNCIO_ID);
        assertThat(estadoFinal.get("usuario_id")).isEqualTo(USUARIO_ID);
        assertThat(estadoFinal.get("titulo").toString()).startsWith("Anuncio concorrente ");
        assertThat(((Number) estadoFinal.get("versao")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM revisao_anuncio WHERE anuncio_id = ?",
                Long.class,
                ANUNCIO_ID)).isEqualTo(1L);
    }

    private UploadResultado executarUpload(String chave, Authentication authentication) {
        try {
            midiasService.enviar(
                    SLUG,
                    new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[] {1, 2, 3}),
                    chave,
                    authentication);
            return new UploadResultado(chave, 200, null);
        } catch (ResponseStatusException exception) {
            return new UploadResultado(chave, exception.getStatusCode().value(), exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            return new UploadResultado(chave, 500, causa(exception));
        }
    }

    private AnuncioEntity anuncioFixture(String titulo) {
        return AnuncioEntity.criarFixtureHomologacao(
                ANUNCIO_ID,
                USUARIO_ID,
                SLUG,
                titulo,
                "Descricao inicial sintetica para concorrencia",
                StatusAnuncio.PUBLICADO,
                StatusModeracaoAnuncio.APROVADO,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    }

    private MidiaValidada uploadValidado() {
        return new MidiaValidada(
                new byte[] {1, 2, 3},
                false,
                "image/png",
                "png",
                "foto.png",
                2,
                3,
                null,
                "a".repeat(64));
    }

    private FotoProcessada fotoProcessada() {
        return new FotoProcessada(
                new byte[] {9, 8, 7},
                "image/jpeg",
                "jpg",
                2,
                3,
                "06df4f7e1394f1c57cc6583fba4d8060a5a66f4f4771c14aeff6b9af8a28c9b3",
                "a".repeat(64),
                1,
                FotoUploadProcessor.WATERMARK_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void seedLocalidade() {
        jdbc.update(
                "INSERT INTO estado (id, uf, nome, nome_normalizado, criado_em) VALUES (?, 'GO', 'Goias', 'goias', now())",
                ESTADO_ID);
        jdbc.update(
                "INSERT INTO cidade (id, estado_id, nome, nome_normalizado, slug, criado_em) VALUES (?, ?, 'Cidade QA', 'cidade qa', 'cidade-qa', now())",
                CIDADE_ID,
                ESTADO_ID);
    }

    private String payloadEdicao(String titulo) {
        return """
                {
                  "titulo":"%s",
                  "descricao":"Descricao valida sintetica para a edicao concorrente",
                  "categoria":"ACOMPANHANTE_FEMININA",
                  "preco":150.00,
                  "uf":"GO",
                  "cidade":"Cidade QA",
                  "bairro":null,
                  "locaisAtendimento":[],
                  "servicos":[],
                  "atendimentoExclusivamenteVirtual":false,
                  "linkConteudo":null
                }
                """.formatted(titulo);
    }

    private static void aguardarBarreira(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Sob lock pessimista somente a primeira transacao chega aqui; o timeout libera o teste.
        }
    }

    private static <T> T obter(java.util.concurrent.Future<T> future, int timeoutSeconds) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("execucao concorrente nao concluiu", exception);
        }
    }

    private static String causa(Throwable throwable) {
        Throwable atual = throwable;
        while (atual.getCause() != null) {
            atual = atual.getCause();
        }
        String message = atual.getMessage();
        return atual.getClass().getSimpleName() + (message == null ? "" : ":" + message);
    }

    private record UploadResultado(String chave, int status, String causa) {
    }

    private static final class Postgres17Fixture implements AutoCloseable {

        private static final Path MIGRATIONS = Path.of(
                "src", "main", "resources", "db", "migration")
                .toAbsolutePath()
                .normalize();
        private static final String OWNER_LABEL = "topsv3.anunciante.fixture.owner";
        private static final String ROLE_LABEL = "topsv3.anunciante.fixture.role";
        private static final ObjectMapper JSON = new ObjectMapper();
        // Somente ensaios descartaveis por reflexao; sem parametro/env que habilite o hook.
        private static BiConsumer<String, Object> lifecycleProbe;

        private String owner = UUID.randomUUID().toString();
        private final String allocationOwner = owner;
        private final String networkName = "topsv3-anunciante-" + owner + "-net";
        private final String containerName = "topsv3-anunciante-" + owner;
        private String networkId;
        private final LinkedHashMap<String, String> containers = new LinkedHashMap<>();
        private final Set<String> ownedVolumes = new LinkedHashSet<>();
        private final Set<String> volumesBefore = new LinkedHashSet<>();
        private final Map<String, String> volumeCreation = new LinkedHashMap<>();
        private final Map<String, String> volumeContainer = new LinkedHashMap<>();
        private final List<Throwable> ambiguousCommands = new ArrayList<>();
        private final String username = "topsv3test";
        private final String credential = UUID.randomUUID().toString() + UUID.randomUUID();
        private int port;
        private long commandDeadline;
        private boolean closing;
        private boolean cleanupInterrupted;
        private Process unsettledCli;

        static Postgres17Fixture start() {
            // O estado existe antes da primeira alocacao, inclusive se o inicializador estatico falhar.
            Postgres17Fixture fixture = new Postgres17Fixture();
            fixture.commandDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
            try {
                fixture.volumesBefore.addAll(fixture.names("volume", "ls", "--quiet"));
                fixture.networkId = fixture.requireId(fixture.command(
                        Map.of(), 10, "network", "create",
                        "--label", OWNER_LABEL + "=" + fixture.owner,
                        fixture.networkName).output());
                fixture.inspectOwnedNetwork();
                fixture.event("network-allocated", fixture.networkId);
                fixture.checkpoint("after-network-created");
                String postgres = fixture.createContainer(
                        "postgres",
                        Map.of("POSTGRES_PASSWORD", fixture.credential),
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_anunciante",
                        "-e", "POSTGRES_USER=" + fixture.username,
                        "-e", "POSTGRES_PASSWORD",
                        "postgres:17-alpine");
                fixture.checkpoint("after-container-created");
                fixture.command(Map.of(), 10, "start", postgres);
                fixture.awaitPostgres(postgres);
                fixture.migrate("migrate");
                fixture.migrate("validate");
                JsonNode inspection = fixture.inspectOwnedContainer(postgres);
                JsonNode bindings = inspection.path("NetworkSettings").path("Ports").path("5432/tcp");
                if (!bindings.isArray() || bindings.size() != 1
                        || !"127.0.0.1".equals(bindings.get(0).path("HostIp").asText())) {
                    throw new IllegalStateException("binding PostgreSQL da fixture nao e loopback unico");
                }
                fixture.port = Integer.parseInt(bindings.get(0).path("HostPort").asText());
                fixture.checkpoint("started");
                return fixture;
            } catch (Throwable original) {
                try {
                    fixture.close();
                } catch (Throwable cleanup) {
                    if (cleanup != original) original.addSuppressed(cleanup);
                }
                throw new IllegalStateException("falha ao iniciar PostgreSQL 17 do teste", original);
            }
        }

        String jdbcUrl() {
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_anunciante";
        }

        String username() {
            return username;
        }

        String credential() {
            return credential;
        }

        @Override
        public synchronized void close() {
            if (!allocationOwner.equals(owner)) {
                throw new IllegalStateException("ownership esperado diverge da alocacao da fixture");
            }
            boolean interrupted = Thread.interrupted();
            cleanupInterrupted = false;
            closing = true;
            // 90s total: inspecoes, paradas, remocoes e prova final. Cada CLI recebe no maximo 10s.
            commandDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            List<Throwable> failures = new ArrayList<>(ambiguousCommands);
            try {
                attempt(failures, this::discoverOwnedNetworks);
                attempt(failures, () -> discoverOwnedContainers(failures));
                List<String> ids = new ArrayList<>(containers.keySet());
                ids.sort((left, right) -> Boolean.compare(
                        "postgres".equals(containers.get(left)), "postgres".equals(containers.get(right))));
                for (String id : ids) attempt(failures, () -> removeContainer(id));
                for (String volume : new ArrayList<>(ownedVolumes)) {
                    attempt(failures, () -> removeVolume(volume));
                }
                attempt(failures, this::removeNetwork);
                verifyAbsent(failures);
                for (Throwable ambiguity : ambiguousCommands) {
                    if (!failures.contains(ambiguity)) failures.add(ambiguity);
                }
                if (!failures.isEmpty()) {
                    IllegalStateException failure = new IllegalStateException(
                            "cleanup da fixture incompleto owner=" + owner, failures.get(0));
                    for (int index = 1; index < failures.size(); index++) {
                        if (failures.get(index) != failure.getCause()) failure.addSuppressed(failures.get(index));
                    }
                    throw failure;
                }
                checkpoint("cleanup-confirmed");
            } finally {
                closing = false;
                if (interrupted || cleanupInterrupted) Thread.currentThread().interrupt();
            }
        }

        private String createContainer(String role, Map<String, String> environment, String... arguments) {
            List<String> args = new ArrayList<>(List.of(
                    "create", "--pull=never", "--name", nameForRole(role),
                    "--label", OWNER_LABEL + "=" + owner,
                    "--label", ROLE_LABEL + "=" + role,
                    "--network", networkId));
            args.addAll(List.of(arguments));
            String id = requireId(command(environment, 10, args.toArray(String[]::new)).output());
            containers.put(id, role);
            captureMounts(id, inspectOwnedContainer(id));
            event("container-allocated-" + role, id);
            return id;
        }

        private void migrate(String action) {
            String id = createContainer(action,
                    Map.of("FLYWAY_PASSWORD", credential),
                    "-e", "FLYWAY_PASSWORD",
                    "-v", MIGRATIONS + ":/flyway/sql:ro",
                    "flyway/flyway:12.10.0",
                    "-url=jdbc:postgresql://" + containerName + ":5432/topsv3_anunciante",
                    "-user=" + username,
                    "-locations=filesystem:/flyway/sql",
                    action);
            command(Map.of(), 120, "start", "--attach", id);
            JsonNode state = inspectOwnedContainer(id).path("State");
            if (state.path("Running").asBoolean(true)
                    || !"exited".equals(state.path("Status").asText())
                    || state.path("ExitCode").asInt(-1) != 0) {
                throw new IllegalStateException("Flyway da fixture nao terminou com sucesso");
            }
        }

        private void awaitPostgres(String id) {
            for (int attempt = 0; attempt < 60; attempt++) {
                CliResult result = invoke(
                        Map.of("PGPASSWORD", credential),
                        10, "exec", "-e", "PGPASSWORD", id,
                        "pg_isready", "--host", "127.0.0.1",
                        "--username", username, "--dbname", "topsv3_anunciante");
                if (result.exit() == 0) return;
                if (result.exit() != 1 && result.exit() != 2) {
                    throw new IllegalStateException("pg_isready falhou fora dos estados transitorios");
                }
                pause(500);
            }
            throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
        }

        private void discoverOwnedNetworks() {
            Set<String> networks = names("network", "ls", "--quiet", "--no-trunc",
                    "--filter", "label=" + OWNER_LABEL + "=" + owner);
            if (networks.size() > 1) throw new IllegalStateException("mais de uma rede para o owner");
            for (String id : networks) {
                requireId(id);
                if (networkId != null && !networkId.equals(id)) {
                    throw new IllegalStateException("ID de rede diverge da alocacao");
                }
                networkId = id;
                inspectOwnedNetwork();
            }
        }

        private void discoverOwnedContainers(List<Throwable> failures) {
            for (String id : names("container", "ls", "--all", "--quiet", "--no-trunc",
                    "--filter", "label=" + OWNER_LABEL + "=" + owner)) {
                attempt(failures, () -> {
                    requireId(id);
                    JsonNode inspected = inspect("container", id);
                    String role = inspected.path("Config").path("Labels").path(ROLE_LABEL).asText();
                    if (!Set.of("postgres", "migrate", "validate").contains(role)) {
                        throw new IllegalStateException("role de container nao pertence a fixture");
                    }
                    String previous = containers.putIfAbsent(id, role);
                    if (previous != null && !previous.equals(role)) {
                        throw new IllegalStateException("role de container mudou");
                    }
                    captureMounts(id, inspectOwnedContainer(id));
                });
            }
        }

        private JsonNode inspectOwnedContainer(String id) {
            if (closing) checkpoint("before-container-inspect");
            JsonNode inspection = inspect("container", id);
            String role = containers.get(id);
            if (role == null || !id.equals(inspection.path("Id").asText())
                    || !owner.equals(inspection.path("Config").path("Labels").path(OWNER_LABEL).asText())
                    || !role.equals(inspection.path("Config").path("Labels").path(ROLE_LABEL).asText())
                    || !("/" + nameForRole(role)).equals(inspection.path("Name").asText())) {
                throw new IllegalStateException("ownership ou identidade de container divergente: " + id);
            }
            return inspection;
        }

        private JsonNode inspectOwnedNetwork() {
            JsonNode inspection = inspect("network", networkId);
            if (!networkId.equals(inspection.path("Id").asText())
                    || !owner.equals(inspection.path("Labels").path(OWNER_LABEL).asText())
                    || !networkName.equals(inspection.path("Name").asText())) {
                throw new IllegalStateException("ownership ou identidade de rede divergente");
            }
            return inspection;
        }

        private void captureMounts(String id, JsonNode inspection) {
            JsonNode mounts = inspection.path("Mounts");
            if (!mounts.isArray()) throw new IllegalStateException("mounts do container ausentes");
            for (JsonNode mount : mounts) {
                if (!"volume".equals(mount.path("Type").asText())) continue;
                String name = mount.path("Name").asText();
                if (name.isBlank()) throw new IllegalStateException("volume sem nome no mount");
                JsonNode volume = inspect("volume", name);
                if (!name.equals(volume.path("Name").asText())) {
                    throw new IllegalStateException("identidade de volume divergente");
                }
                boolean anonymous = volume.path("Labels").has("com.docker.volume.anonymous");
                event("volume-mount", "container=" + id + ",volume=" + name
                        + ",destination=" + mount.path("Destination").asText());
                if (name.matches("[a-f0-9]{64}") && !volumesBefore.contains(name) && anonymous) {
                    String created = volume.path("CreatedAt").asText();
                    if (created.isBlank()) throw new IllegalStateException("volume sem identidade temporal");
                    String recorded = volumeCreation.putIfAbsent(name, created);
                    if (recorded != null && !recorded.equals(created)) {
                        throw new IllegalStateException("volume associado foi substituido");
                    }
                    ownedVolumes.add(name);
                    volumeContainer.putIfAbsent(name, id);
                    event("owned-volume", name);
                } else {
                    event("preserved-volume", name);
                }
            }
        }

        private void removeContainer(String id) {
            if (!names("container", "ls", "--all", "--quiet", "--no-trunc").contains(id)) return;
            JsonNode inspection = inspectOwnedContainer(id);
            captureMounts(id, inspection);
            if ("postgres".equals(containers.get(id))) {
                Set<String> live = names("container", "ls", "--all", "--quiet", "--no-trunc");
                for (var entry : containers.entrySet()) {
                    if (!"postgres".equals(entry.getValue()) && live.contains(entry.getKey())) {
                        throw new IllegalStateException("consumidor Flyway ainda existe antes de remover PostgreSQL");
                    }
                }
            }
            checkpoint("before-container-remove");
            command(Map.of(), 10, "rm", "--force", id);
            if (names("container", "ls", "--all", "--quiet", "--no-trunc").contains(id)) {
                throw new IllegalStateException("container ainda existe depois de rm: " + id);
            }
            event("container-absent", id);
        }

        private void removeVolume(String name) {
            if (!names("volume", "ls", "--quiet").contains(name)) return;
            JsonNode volume = inspect("volume", name);
            if (volumesBefore.contains(name) || !ownedVolumes.contains(name)
                    || !volumeContainer.containsKey(name)
                    || !name.equals(volume.path("Name").asText())
                    || !volumeCreation.get(name).equals(volume.path("CreatedAt").asText())
                    || !volume.path("Labels").has("com.docker.volume.anonymous")) {
                throw new IllegalStateException("ownership de volume nao comprovado: " + name);
            }
            Set<String> consumers = names("container", "ls", "--all", "--quiet", "--no-trunc",
                    "--filter", "volume=" + name);
            if (!consumers.isEmpty()) {
                throw new IllegalStateException("volume compartilhado ou ainda consumido, preservado: " + name);
            }
            command(Map.of(), 10, "volume", "rm", name);
            if (names("volume", "ls", "--quiet").contains(name)) {
                throw new IllegalStateException("volume permanece depois da remocao: " + name);
            }
            event("volume-absent", name);
        }

        private void removeNetwork() {
            if (networkId == null) return;
            if (!names("network", "ls", "--quiet", "--no-trunc").contains(networkId)) return;
            JsonNode inspection = inspectOwnedNetwork();
            if (!inspection.path("Containers").isObject() || !inspection.path("Containers").isEmpty()) {
                throw new IllegalStateException("rede da fixture ainda possui consumidores, preservada");
            }
            checkpoint("before-network-remove");
            command(Map.of(), 10, "network", "rm", networkId);
            if (names("network", "ls", "--quiet", "--no-trunc").contains(networkId)) {
                throw new IllegalStateException("rede permanece depois da remocao");
            }
            event("network-absent", networkId);
        }

        private void verifyAbsent(List<Throwable> failures) {
            attempt(failures, () -> {
                Set<String> actualContainers = names("container", "ls", "--all", "--quiet", "--no-trunc");
                Set<String> byOwner = names("container", "ls", "--all", "--quiet", "--no-trunc",
                        "--filter", "label=" + OWNER_LABEL + "=" + owner);
                if (containers.keySet().stream().anyMatch(actualContainers::contains) || !byOwner.isEmpty()) {
                    throw new IllegalStateException("containers proprios permanecem no daemon");
                }
            });
            attempt(failures, () -> {
                Set<String> actualNetworks = names("network", "ls", "--quiet", "--no-trunc");
                Set<String> byOwner = names("network", "ls", "--quiet", "--no-trunc",
                        "--filter", "label=" + OWNER_LABEL + "=" + owner);
                if ((networkId != null && actualNetworks.contains(networkId)) || !byOwner.isEmpty()) {
                    throw new IllegalStateException("rede propria permanece no daemon");
                }
            });
            attempt(failures, () -> {
                Set<String> actualVolumes = names("volume", "ls", "--quiet");
                if (ownedVolumes.stream().anyMatch(actualVolumes::contains)) {
                    throw new IllegalStateException("volumes associados proprios permanecem no daemon");
                }
            });
        }

        private JsonNode inspect(String kind, String id) {
            String output = command(Map.of(), 10, kind, "inspect", id).output();
            try {
                JsonNode array = JSON.readTree(output);
                if (array == null || !array.isArray() || array.size() != 1 || !array.get(0).isObject()) {
                    throw new IllegalStateException("inspecao Docker incompleta");
                }
                return array.get(0);
            } catch (IOException exception) {
                throw new IllegalStateException("JSON invalido na inspecao Docker", exception);
            }
        }

        private Set<String> names(String... args) {
            String output = command(Map.of(), 10, args).output();
            Set<String> result = new LinkedHashSet<>();
            for (String line : output.lines().toList()) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || !result.add(name)) {
                    throw new IllegalStateException("listagem Docker invalida ou duplicada");
                }
            }
            return result;
        }

        private String requireId(String output) {
            String id = output.trim();
            if (!id.matches("[a-f0-9]{64}")) throw new IllegalStateException("ID Docker incompleto");
            return id;
        }

        private String nameForRole(String role) {
            if (!Set.of("postgres", "migrate", "validate").contains(role)) {
                throw new IllegalStateException("role nao autorizado");
            }
            return "postgres".equals(role) ? containerName : containerName + "-" + role;
        }

        private CliResult command(Map<String, String> environment, int seconds, String... args) {
            CliResult result = invoke(environment, seconds, args);
            if (result.exit() != 0) {
                String detail = result.output().replace(credential, "[FIXTURE_VALUE_REDACTED]");
                if (List.of(args).contains("inspect")) {
                    // Inspect pode incluir Config.Env; conservar somente linhas explicitas de erro.
                    detail = detail.lines().filter(line -> line.matches("(?i)^(error|docker:|unable|warning).*"))
                            .reduce("", (left, right) -> left + right + "\n");
                }
                if (detail.length() > 8_192) detail = detail.substring(0, 8_192) + " [truncated]";
                throw new IllegalStateException("Docker " + args[0] + " falhou exit=" + result.exit()
                        + (detail.isBlank() ? "" : "; " + detail.trim()));
            }
            return result;
        }

        private CliResult invoke(Map<String, String> environment, int seconds, String... args) {
            if (!ambiguousCommands.isEmpty() && mutates(args)) {
                // CLI encerrada nao demonstra que a requisicao do daemon deixou de estar em voo.
                // So observar; recuperacao externa do daemon proprio permanece uma etapa separada.
                throw new IllegalStateException("mutacao recusada apos comando Docker ambiguo");
            }
            if (unsettledCli != null) {
                if (!unsettledCli.isAlive()) unsettledCli = null;
                else if (mutates(args)) {
                    throw new IllegalStateException("mutacao recusada: CLI anterior ainda esta viva");
                }
            }
            long available = TimeUnit.NANOSECONDS.toMillis(commandDeadline - System.nanoTime());
            long limit = Math.min(TimeUnit.SECONDS.toMillis(closing ? Math.min(seconds, 10) : seconds), available);
            // Reserva dois segundos dentro do saldo para terminar a CLI; nunca para supor termino do daemon.
            if (limit <= 2_000) throw new IllegalStateException("deadline de lifecycle esgotado");
            long cliDeadline = Math.min(commandDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limit));
            Path output = null;
            Process process = null;
            boolean restoreInterrupt = false;
            try {
                output = Files.createTempFile("anunciante-fixture-cli-", ".log");
                List<String> command = new ArrayList<>(List.of("docker"));
                command.addAll(List.of(args));
                // Argumentos sao locais/sinteticos; valores de ambiente e inspect completo nao sao logados.
                String commandEvidence = JSON.writeValueAsString(command);
                System.out.println("ANUNCIANTE_FIXTURE_CLI owner=" + owner + " args=" + commandEvidence);
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
                        .redirectOutput(output.toFile());
                builder.environment().putAll(environment);
                process = builder.start();
                long processBudget = TimeUnit.NANOSECONDS.toMillis(cliDeadline - System.nanoTime()) - 2_000;
                if (processBudget <= 0 || !process.waitFor(processBudget, TimeUnit.MILLISECONDS)) {
                    IllegalStateException failure = new IllegalStateException(
                            "timeout da CLI Docker; estado do daemon nao confirmado: " + args[0]);
                    ambiguousCommands.add(failure);
                    System.out.println("ANUNCIANTE_FIXTURE_CLI owner=" + owner + " exit=TIMEOUT");
                    throw failure;
                }
                if (System.nanoTime() >= cliDeadline) {
                    throw new IllegalStateException("comando Docker terminou fora do deadline global");
                }
                if (Files.size(output) > 1_048_576) throw new IllegalStateException("saida Docker excede limite");
                System.out.println("ANUNCIANTE_FIXTURE_CLI owner=" + owner + " exit=" + process.exitValue());
                return new CliResult(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
            } catch (InterruptedException exception) {
                IllegalStateException failure = new IllegalStateException("CLI Docker interrompida", exception);
                ambiguousCommands.add(failure);
                restoreInterrupt = true;
                throw failure;
            } catch (IOException exception) {
                throw new IllegalStateException("falha de transporte local da CLI Docker", exception);
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                    while (process.isAlive() && System.nanoTime() < cliDeadline) {
                        try {
                            process.waitFor(Math.max(1, Math.min(100,
                                    TimeUnit.NANOSECONDS.toMillis(cliDeadline - System.nanoTime()))),
                                    TimeUnit.MILLISECONDS);
                        } catch (InterruptedException exception) {
                            restoreInterrupt = true;
                        }
                    }
                    if (process.isAlive()) {
                        unsettledCli = process;
                        ambiguousCommands.add(new IllegalStateException("CLI Docker nao terminou apos interrupcao"));
                    }
                }
                if (output != null && (process == null || !process.isAlive())) {
                    try {
                        Files.deleteIfExists(output);
                    } catch (IOException exception) {
                        ambiguousCommands.add(new IllegalStateException("saida temporaria da CLI nao foi liberada", exception));
                    }
                }
                if (restoreInterrupt) Thread.currentThread().interrupt();
            }
        }

        private boolean mutates(String... args) {
            return "create".equals(args[0]) || "start".equals(args[0]) || "rm".equals(args[0])
                    || (args.length > 1 && ("network".equals(args[0]) || "volume".equals(args[0]))
                    && ("create".equals(args[1]) || "rm".equals(args[1])));
        }

        private void pause(long millis) {
            if (commandDeadline - System.nanoTime() <= TimeUnit.MILLISECONDS.toNanos(millis)) {
                throw new IllegalStateException("deadline de inicializacao esgotado");
            }
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("inicializacao interrompida", exception);
            }
        }

        private void checkpoint(String stage) {
            event(stage, "-");
            BiConsumer<String, Object> probe = lifecycleProbe;
            if (probe != null) probe.accept(stage, this);
        }

        private void event(String stage, String resource) {
            System.out.println("ANUNCIANTE_FIXTURE stage=" + stage + " owner=" + owner + " resource=" + resource);
        }

        private void attempt(List<Throwable> failures, Runnable operation) {
            try {
                operation.run();
            } catch (Throwable failure) {
                failures.add(failure);
            } finally {
                if (Thread.interrupted()) cleanupInterrupted = true;
            }
        }

        private record CliResult(int exit, String output) { }
    }
}
