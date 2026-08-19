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
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
        when(atualizacaoValidator.textoBusca(any())).thenReturn("documento de busca qa");
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

        private final String network;
        private final String container;
        private final String username;
        private final String credential;
        private final int port;

        private Postgres17Fixture(
                String network,
                String container,
                String username,
                String credential,
                int port) {
            this.network = network;
            this.container = container;
            this.username = username;
            this.credential = credential;
            this.port = port;
        }

        static Postgres17Fixture start() {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String network = "topsv3-anunciante-concurrency-" + suffix + "-net";
            String container = "topsv3-anunciante-concurrency-" + suffix;
            String username = "topsv3test";
            String credential = UUID.randomUUID().toString() + UUID.randomUUID();
            try {
                command("docker", "network", "create", network);
                command(
                        Map.of("POSTGRES_PASSWORD", credential),
                        "docker", "run", "--pull=never", "-d", "--name", container,
                        "--network", network,
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_anunciante",
                        "-e", "POSTGRES_USER=" + username,
                        "-e", "POSTGRES_PASSWORD",
                        "postgres:17-alpine");
                awaitPostgres(container, username, credential);
                migrate(network, container, username, credential, "migrate");
                migrate(network, container, username, credential, "validate");
                return new Postgres17Fixture(
                        network,
                        container,
                        username,
                        credential,
                        mappedPort(container));
            } catch (Exception exception) {
                commandIgnoringFailure("docker", "rm", "-f", container);
                commandIgnoringFailure("docker", "network", "rm", network);
                throw new IllegalStateException("falha ao iniciar PostgreSQL 17 do teste", exception);
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
        public void close() {
            commandIgnoringFailure("docker", "rm", "-f", container);
            commandIgnoringFailure("docker", "network", "rm", network);
        }

        private static void migrate(
                String network,
                String container,
                String username,
                String credential,
                String action) throws Exception {
            command(
                    Map.of("FLYWAY_PASSWORD", credential),
                    "docker", "run", "--pull=never", "--rm", "--network", network,
                    "-e", "FLYWAY_PASSWORD",
                    "-v", MIGRATIONS + ":/flyway/sql:ro",
                    "flyway/flyway:12.10.0",
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_anunciante",
                    "-user=" + username,
                    "-locations=filesystem:/flyway/sql",
                    action);
        }

        private static void awaitPostgres(
                String container,
                String username,
                String credential) throws Exception {
            for (int attempt = 0; attempt < 60; attempt++) {
                if (commandIgnoringFailure(
                        Map.of("PGPASSWORD", credential),
                        "docker", "exec", "-e", "PGPASSWORD", container,
                        "pg_isready", "--host", "127.0.0.1",
                        "--username", username, "--dbname", "topsv3_anunciante") == 0) {
                    return;
                }
                Thread.sleep(500L);
            }
            throw new IllegalStateException("PostgreSQL 17 nao ficou pronto");
        }

        private static int mappedPort(String container) throws Exception {
            String output = command("docker", "port", container, "5432/tcp").trim();
            return Integer.parseInt(output.substring(output.lastIndexOf(':') + 1));
        }

        private static String command(String... args) throws Exception {
            return command(Map.of(), args);
        }

        private static String command(
                Map<String, String> environment,
                String... args) throws Exception {
            ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
            builder.environment().putAll(environment);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(args[0] + " falhou: " + output.lines().findFirst().orElse("sem detalhe"));
            }
            return output;
        }

        private static int commandIgnoringFailure(String... args) {
            return commandIgnoringFailure(Map.of(), args);
        }

        private static int commandIgnoringFailure(
                Map<String, String> environment,
                String... args) {
            try {
                ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
                builder.environment().putAll(environment);
                Process process = builder.start();
                process.getInputStream().readAllBytes();
                return process.waitFor();
            } catch (Exception exception) {
                return -1;
            }
        }
    }
}
