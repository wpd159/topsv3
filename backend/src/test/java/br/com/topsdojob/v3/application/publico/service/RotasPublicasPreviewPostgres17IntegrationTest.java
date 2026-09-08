package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import br.com.topsdojob.v3.TopsDoJobBackendApplication;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/** Actual Tomcat/PG17/pool5/SigV4 HTTP; only fixture data and HEAD latency are synthetic. */
@SpringBootTest(classes = {TopsDoJobBackendApplication.class,
        RotasPublicasPreviewPostgres17IntegrationTest.TestBeans.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = RotasPublicasPreviewPostgres17IntegrationTest.Initializer.class)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "LOCALIDADES_POSTGRES17_ENABLED", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RotasPublicasPreviewPostgres17IntegrationTest {
    private static final String CITY = "/api/public/acompanhantes/CE/fortaleza?pagina=0&tamanho=20&ordemSeed=27";
    private static final String SITEMAP = "/api/public/seo/sitemap";
    private static final String PUBLIC = "https://public.example.invalid/";
    private static final AtomicInteger HTTP_ACTIVE = new AtomicInteger();
    private static LocalS3 remote;
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HikariDataSource pool;
    @Autowired private LocalidadesConsultaCoordenador coordinator;
    @Autowired private R2StorageProperties properties;
    @Autowired private ObjectStorage storage;
    @Autowired private MidiaRestritaDerivacaoService derivation;
    @Autowired private ObjectMapper json;
    @Autowired private ConfigurableApplicationContext context;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void prepare() {
        drained();
        assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
        assertThat(pool.getConnectionTimeout()).isEqualTo(30_000);
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view", Boolean.class)).isTrue();
        assertThat(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor")).isFalse();
        assertThat(jdbc.queryForObject("show server_version_num", Integer.class)).isBetween(170000, 179999);
        assertThat(storage.getClass().getSimpleName()).isEqualTo("R2ObjectStorage");
        // Do not print potentially misbound credentials or an external endpoint on failure.
        assertThat(remote.endpoint().equals(properties.getEndpoint())).as("loopback R2 binding").isTrue();
        assertThat("synthetic-access".equals(properties.getAccessKey())).isTrue();
        assertThat("synthetic-signing-value".equals(properties.getSigningValue())).isTrue();
        jdbc.execute("truncate usuario, estado, arquivo_midia cascade");
        remote.reset(pool);
    }

    @AfterEach
    void releaseAndCheckActualWork() throws Exception {
        remote.release.countDown();
        drained();
        assertThat(remote.failure.get()).isNull();
        assertThat(remote.forbidden.get()).isZero();
        assertThat(remote.lists.get()).isZero();
        for (String setting : List.of("statement_timeout", "lock_timeout", "transaction_timeout"))
            assertThat(jdbc.queryForObject("show " + setting, String.class)).isEqualTo("0");
        try (Connection connection = pool.getConnection()) { assertThat(connection.getNetworkTimeout()).isZero(); }
        poolFree();
    }

    @AfterAll
    static void stopOnlyOwnedResources() throws Exception {
        Exception first = null;
        try { if (remote != null) remote.close(); } catch (Exception error) { first = error; }
        try { LocalidadesConsultaPostgres17IntegrationTest.PostgresSupport.stop(); }
        catch (Exception error) { if (first == null) first = error; else first.addSuppressed(error); }
        if (first != null) throw first;
    }

    @ParameterizedTest
    @ValueSource(strings = {"city", "sitemap"})
    void vinteCardsComQuatroFotosNaoConsultamPreviewsDescartados(String route) throws Exception {
        Fixture fixture = seed(20, false);
        // Sixty discarded HEADs at 125 ms would alone cost 7.5 seconds.
        // Seed is excluded; the complete real HTTP body is included in this clock.
        for (int round = 0; round < 2; round++) {
            long start = System.nanoTime();
            Reply response = get(route.equals("city") ? CITY : SITEMAP);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(response.status()).isEqualTo(200);
            assertThat(elapsedMs).as("complete body below the 4 s margin").isLessThan(4000);
            assertThat(remote.heads.get()).isZero();
            assertThat(remote.lists.get()).isZero();
            assertSafe(response.raw());
            if (route.equals("city")) assertFreeCards(response.body(), fixture);
            else assertSitemap(response.body(), fixture);
            drained();
            System.out.printf("ROTAS_PREVIEW_HTTP route=%s round=%d status=200 completeMs=%.3f ads=20 heads=0 lists=0 pool=0 coordinator=0%n",
                    route, round + 1, elapsedMs);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 503})
    void carrosselNecessarioPreservaPreviewPendenciasECachePositivo(int storageStatus) throws Exception {
        Fixture fixture = seed(1, true);
        remote.status = storageStatus;
        for (int round = 1; round <= 2; round++) {
            Reply response = get(CITY);
            assertThat(response.status()).isEqualTo(200);
            JsonNode card = response.body().path("itens").get(0);
            assertThat(card.path("id").asText()).isEqualTo(fixture.ads().get(0).id().toString());
            assertThat(card.path("beneficiosPublicos"))
                    .isEqualTo(json.createArrayNode().add("Carrossel de fotos"));
            assertGallery(card.path("midias"), fixture.ads().get(0), storageStatus);
            assertSafe(response.raw());
            assertThat(remote.heads.get()).isEqualTo(storageStatus == 200 ? 3 : 3 * round);
            assertThat(remote.maxPool.get()).as("preview I/O retains no JDBC connection, including with OSIV").isZero();
            assertThat(remote.maxWaiting.get()).isZero();
            drained();
        }
    }

    @Test
    void cardSemFotoLivreContinuaExibindoSeuPreviewNecessario() throws Exception {
        Fixture fixture = seed(1, false);
        Ad ad = fixture.ads().get(0);
        jdbc.update("delete from anuncio_midia where id=?", ad.media().get(0).id());
        Reply response = get(CITY);
        assertThat(response.status()).isEqualTo(200);
        JsonNode media = response.body().path("itens").get(0).path("midias");
        assertThat(media.size()).isEqualTo(1);
        assertRestricted(media.get(0), ad.media().get(1), 200);
        assertThat(remote.heads.get()).isEqualTo(1);
        assertThat(remote.maxPool.get()).isZero();
        assertSafe(response.raw());
    }

    @ParameterizedTest
    @ValueSource(strings = {"link-delete", "link-pending", "link-reclassify", "checksum", "file-id",
            "unpublish", "remove", "owner-disabled", "carousel-revoked"})
    void releituraFinalNaoReutilizaMidiaOuAnuncioAlteradoDuranteHead(String mutation) throws Exception {
        Fixture fixture = seed(1, true);
        Ad ad = fixture.ads().get(0);
        remote.hold();
        CompletableFuture<HttpResponse<byte[]>> pending = async(CITY);
        try {
            assertThat(remote.entered.await(5, TimeUnit.SECONDS)).as("actual SigV4 HEAD entered").isTrue();
            poolFree();
            assertThat(remote.active.get()).isEqualTo(1);
            Media changed = ad.media().stream().filter(m -> m.previewKey().equals(remote.gatedKey)).findFirst().orElseThrow();
            mutate(mutation, ad, changed);
            poolFree();
            remote.release.countDown();
            Reply response = reply(pending.get(10, TimeUnit.SECONDS));
            int expectedStatus = switch (mutation) {
                case "checksum", "file-id" -> 503;
                case "unpublish", "remove", "owner-disabled" -> 404;
                default -> 200;
            };
            assertThat(response.status()).as(mutation).isEqualTo(expectedStatus);
            assertSafe(response.raw());
            assertThat(response.raw()).doesNotContain(PUBLIC + changed.previewKey());
            if (expectedStatus == 200) {
                JsonNode card = response.body().path("itens").get(0);
                assertThat(card.path("id").asText()).isEqualTo(ad.id().toString());
                JsonNode media = card.path("midias");
                if (mutation.equals("carousel-revoked")) {
                    assertThat(media.size()).isEqualTo(1);
                    assertFree(media.get(0), ad.media().get(0));
                    assertThat(card.path("beneficiosPublicos")).isEqualTo(json.createArrayNode());
                } else if (mutation.equals("link-reclassify")) {
                    JsonNode reclassified = find(media, changed.id());
                    assertThat(reclassified.path("visibilidadeMidia").asText()).isEqualTo("LIVRE");
                    assertThat(reclassified.path("previewUrl").isNull()).isTrue();
                    assertThat(reclassified.path("urlPublica").isNull()).isTrue();
                    assertThat(reclassified.path("pendenciaMidia").asText()).isEqualTo("PENDENTE_URL_PUBLICA_MIDIA_CDN");
                } else {
                    assertThat(media.size()).isEqualTo(3);
                    assertThat(ids(media)).doesNotContain(changed.id().toString());
                }
            }
            assertThat(remote.heads.get()).as("no unverified fourth HEAD inside the final read").isEqualTo(3);
            assertThat(remote.maxPool.get()).isZero();
            assertThat(remote.maxWaiting.get()).isZero();
            drained();
        } finally {
            remote.release.countDown();
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    @Test
    void consumidorHttpAbortadoNaoDeixaConexaoTrabalhoOuCallbackRetido() throws Exception {
        seed(1, true);
        remote.hold();
        CompletableFuture<HttpResponse<byte[]>> pending = async(CITY);
        try {
            assertThat(remote.entered.await(5, TimeUnit.SECONDS)).isTrue();
            poolFree();
            assertThat(pending.cancel(true)).isTrue();
            // HttpClient may propagate cancellation through a dependent future before
            // MinimalFuture.cancel reaches super.cancel; isCancelled only sees a direct cause.
            Throwable cancellation = catchThrowable(() -> pending.get(1, TimeUnit.SECONDS));
            while (cancellation instanceof ExecutionException || cancellation instanceof CompletionException)
                cancellation = cancellation.getCause();
            assertThat(cancellation).isInstanceOf(CancellationException.class);
            assertThat(pending.isCompletedExceptionally()).isTrue();
        } finally { remote.release.countDown(); }
        // Cancelled client Future is not evidence that the actual servlet/producer stopped.
        drained();
        assertThat(remote.maxPool.get()).isZero();
        assertThat(remote.maxWaiting.get()).isZero();
        Reply recovered = get(CITY);
        assertThat(recovered.status()).isEqualTo(200);
        assertThat(recovered.body().path("itens").size()).isEqualTo(1);
        assertSafe(recovered.raw());
    }

    @Test
    void perfilAnonimoPreservaGaleriaCompletaSemAlterarSeuContrato() throws Exception {
        Fixture fixture = seed(1, false);
        Ad ad = fixture.ads().get(0);
        Reply response = get("/api/public/anuncios/" + ad.slug());
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("id").asText()).isEqualTo(ad.id().toString());
        assertThat(response.body().path("slug").asText()).isEqualTo(ad.slug());
        assertGallery(response.body().path("midias"), ad, 200);
        assertThat(remote.heads.get()).isEqualTo(3);
        assertSafe(response.raw());
        // Full-profile transaction semantics are intentionally outside the card rewrite.
    }

    private void assertFreeCards(JsonNode body, Fixture fixture) {
        JsonNode items = body.path("itens");
        assertThat(items.size()).isEqualTo(20);
        assertThat(ids(items)).containsExactlyInAnyOrderElementsOf(fixture.ads().stream().map(a -> a.id().toString()).toList());
        assertThat(body.path("paginacao").path("totalItens").asInt()).isEqualTo(20);
        assertThat(body.path("paginacao").path("ordemSeed").asText()).isEqualTo("27");
        assertThat(body.path("localidade").path("uf").asText()).isEqualTo("CE");
        assertThat(body.path("localidade").path("cidadeSlug").asText()).isEqualTo("fortaleza");
        for (Ad ad : fixture.ads()) {
            JsonNode card = find(items, ad.id());
            assertThat(card.path("slug").asText()).isEqualTo(ad.slug());
            assertThat(card.path("midias").size()).isEqualTo(1);
            assertFree(card.path("midias").get(0), ad.media().get(0));
        }
    }

    private void assertSitemap(JsonNode body, Fixture fixture) {
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(20);
        List<String> slugs = new ArrayList<>();
        body.forEach(item -> {
            slugs.add(item.path("slug").asText());
            assertThat(item.path("estadoUf").asText()).isEqualTo("CE");
            assertThat(item.path("cidadeSlug").asText()).isEqualTo("fortaleza");
            assertThat(item.path("bairroSlug").asText()).isEqualTo("bairro-seguro");
            assertThat(item.path("publico").asBoolean()).isTrue();
            assertThat(item.path("indexavel").asBoolean()).isTrue();
            assertThat(OffsetDateTime.parse(item.path("atualizadoEm").asText()).toInstant())
                    .as("lastmod includes restricted links without resolving their previews")
                    .isEqualTo(fixture.lastMediaUpdate().toInstant());
        });
        assertThat(slugs).containsExactlyElementsOf(fixture.ads().stream().map(Ad::slug).sorted().toList());
    }

    private void assertGallery(JsonNode media, Ad ad, int status) {
        assertThat(media.size()).isEqualTo(4);
        assertThat(ids(media)).containsExactlyElementsOf(ad.media().stream().map(m -> m.id().toString()).toList());
        assertFree(media.get(0), ad.media().get(0));
        for (int i = 1; i < 4; i++) assertRestricted(media.get(i), ad.media().get(i), status);
    }

    private void assertFree(JsonNode value, Media expected) {
        assertThat(value.path("id").asText()).isEqualTo(expected.id().toString());
        assertThat(value.path("tipo").asText()).isEqualTo("FOTO");
        assertThat(value.path("ordem").asInt()).isZero();
        assertThat(value.path("visibilidadeMidia").asText()).isEqualTo("LIVRE");
        assertThat(value.path("autorizada").asBoolean()).isTrue();
        assertThat(value.path("urlPublica").asText()).isEqualTo(PUBLIC + expected.originalKey());
        assertThat(value.path("previewUrl").isNull()).isTrue();
        assertThat(value.path("pendenciaMidia").isNull()).isTrue();
    }

    private void assertRestricted(JsonNode value, Media expected, int status) {
        assertThat(value.path("id").asText()).isEqualTo(expected.id().toString());
        assertThat(value.path("tipo").asText()).isEqualTo("FOTO");
        assertThat(value.path("ordem").asInt()).isEqualTo(expected.order());
        assertThat(value.path("visibilidadeMidia").asText()).isEqualTo("RESTRITA_18");
        assertThat(value.path("autorizada").asBoolean()).isFalse();
        assertThat(value.path("urlPublica").isNull()).isTrue();
        if (status == 200) {
            assertThat(value.path("previewUrl").asText()).isEqualTo(PUBLIC + expected.previewKey());
            assertThat(value.path("pendenciaMidia").asText()).isEqualTo("MIDIA_RESTRITA_IDADE");
        } else {
            assertThat(value.path("previewUrl").isNull()).isTrue();
            assertThat(value.path("pendenciaMidia").asText()).isEqualTo("PENDENTE_DERIVACAO_RESTRITA");
        }
    }

    private void assertSafe(String body) {
        assertThat(body).doesNotContain("privadas-teste", "hml/privadas/", "synthetic-access",
                "synthetic-signing-value", "Authorization", "X-Amz-Signature");
    }

    private void mutate(String action, Ad ad, Media media) {
        switch (action) {
            case "link-delete" -> jdbc.update("delete from anuncio_midia where id=?", media.id());
            case "link-pending" -> jdbc.update("update anuncio_midia set status='PENDENTE' where id=?", media.id());
            case "link-reclassify" -> jdbc.update("update anuncio_midia set visibilidade_midia='LIVRE' where id=?", media.id());
            case "checksum" -> jdbc.update("update arquivo_midia set sha256=? where id=?", "a".repeat(64), media.file());
            case "file-id" -> {
                UUID replacement = UUID.randomUUID();
                jdbc.update("""
                        insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                        values (?,'R2','privadas-teste',?,'image/jpeg',1024,'VALIDADO',now())
                        """, replacement, "hml/privadas/" + replacement + ".jpg");
                jdbc.update("update anuncio_midia set arquivo_midia_id=? where id=?", replacement, media.id());
            }
            case "unpublish" -> jdbc.update("update anuncio set status='PAUSADO' where id=?", ad.id());
            case "remove" -> jdbc.update("update anuncio set removido_em=now() where id=?", ad.id());
            case "owner-disabled" -> jdbc.update("update usuario set status='DESATIVADO' where id=?", ad.user());
            case "carousel-revoked" -> jdbc.update("update ativacao_beneficio set status='CANCELADA' where anuncio_id=?", ad.id());
            default -> throw new IllegalArgumentException(action);
        }
    }

    private Fixture seed(int count, boolean carousel) {
        OffsetDateTime stamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).withNano(0);
        UUID state = UUID.randomUUID(), city = UUID.randomUUID(), neighborhood = UUID.randomUUID();
        jdbc.update("insert into estado(id,uf,nome,nome_normalizado,criado_em) values (?,'CE','Ceara sintetico','ceara sintetico',?)", state, stamp);
        jdbc.update("insert into cidade(id,estado_id,nome,nome_normalizado,slug,criado_em) values (?,?,'Fortaleza sintetica','fortaleza sintetica','fortaleza',?)", city, state, stamp);
        jdbc.update("insert into bairro(id,cidade_id,nome,nome_normalizado,slug,criado_em) values (?,?,'Bairro seguro','bairro seguro','bairro-seguro',?)", neighborhood, city, stamp);
        List<Ad> ads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID user = UUID.randomUUID(), ad = UUID.randomUUID();
            String slug = "rotas-seguras-" + ad;
            jdbc.update("insert into usuario(id,nome,status,tipo_conta,criado_em,atualizado_em,versao) values (?,'Pessoa sintetica','ATIVO','ANUNCIANTE',?,?,0)", user, stamp, stamp);
            jdbc.update("""
                    insert into anuncio(id,usuario_id,slug,titulo,descricao,status,status_moderacao,categoria,
                    atendimento_exclusivamente_virtual,publicado_em,criado_em,atualizado_em,versao)
                    values (?,?,?,'Perfil sintetico seguro',?,'PUBLICADO','APROVADO','ACOMPANHANTE_FEMININA',false,?,?,?,0)
                    """, ad, user, slug, "Descricao sintetica segura para validar contratos de rotas publicas. ".repeat(4), stamp, stamp, stamp);
            jdbc.update("insert into anuncio_localizacao(anuncio_id,estado_id,cidade_id,bairro_id,criado_em,atualizado_em) values (?,?,?,?,?,?)", ad, state, city, neighborhood, stamp, stamp);
            List<Media> media = new ArrayList<>();
            for (int order = 0; order < 4; order++) {
                UUID file = UUID.randomUUID(), link = UUID.randomUUID();
                boolean free = order == 0;
                String original = (free ? "hml/publicas/" : "hml/privadas/") + file + ".jpg";
                jdbc.update("""
                        insert into arquivo_midia(id,storage_provider,bucket,chave_objeto,mime_type,tamanho_bytes,status_arquivo,criado_em)
                        values (?,'R2',?,?,'image/jpeg',1024,'VALIDADO',?)
                        """, file, free ? "publicas-teste" : "privadas-teste", original, stamp);
                jdbc.update("""
                        insert into anuncio_midia(id,anuncio_id,arquivo_midia_id,tipo,finalidade,ordem,status,visibilidade_midia,criado_em,atualizado_em)
                        values (?,?,?,'FOTO','GALERIA',?,'PUBLICAVEL',?,?,?)
                        """, link, ad, file, order, free ? "LIVRE" : "RESTRITA_18", stamp, stamp.plusHours(order));
                String preview = previewKey(file);
                if (!free) remote.keys.add(preview);
                media.add(new Media(link, file, order, original, preview));
            }
            Ad owner = new Ad(ad, user, slug, List.copyOf(media));
            ads.add(owner);
            if (carousel) carousel(owner, stamp);
        }
        assertThat(jdbc.queryForObject("select count(*) from anuncio", Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(*) from anuncio_midia", Integer.class)).isEqualTo(count * 4);
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(derivation);
        Object cache = org.springframework.test.util.ReflectionTestUtils.getField(target, "previewsConfirmados");
        assertThat(cache).isInstanceOf(Set.class);
        assertThat(remote.keys.stream().noneMatch(((Set<?>) cache)::contains))
                .as("every newly generated preview identity is cold, without resetting production cache").isTrue();
        return new Fixture(List.copyOf(ads), stamp.plusHours(3));
    }

    private void carousel(Ad ad, OffsetDateTime stamp) {
        UUID benefit = jdbc.queryForObject("select id from beneficio_premium where codigo='CARROSSEL_FOTOS'", UUID.class);
        assertThat(benefit).isNotNull();
        UUID group = UUID.randomUUID();
        jdbc.update("""
                insert into grupo_ativacao_beneficio(id,tipo,origem,usuario_id,anuncio_id,validade_inicio_em,validade_fim_em,status,criado_em,atualizado_em)
                values (?,'PACOTE','ADMIN',?,?,?,?,'ATIVO',?,?)
                """, group, ad.user(), ad.id(), stamp, stamp.plusDays(3), stamp, stamp);
        jdbc.update("""
                insert into ativacao_beneficio(id,beneficio_id,usuario_id,anuncio_id,grupo_ativacao_id,origem,inicio_em,fim_em,status,custo_creditos_snapshot,criado_em)
                values (?,?,?,?,?,'ADMIN',?,?,'ATIVA',0,?)
                """, UUID.randomUUID(), benefit, ad.user(), ad.id(), group, stamp, stamp.plusDays(3), stamp);
    }

    private void poolFree() {
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }

    private void drained() {
        await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(HTTP_ACTIVE.get()).isZero();
            assertThat(remote.active.get()).isZero();
            assertThat(remote.executor.getActiveCount()).isZero();
            assertThat(remote.executor.getQueue()).isEmpty();
            poolFree();
            assertThat(coordinator.trabalhoRemanescente()).isZero();
            assertThat(coordinator.consumidoresAguardando()).isZero();
            assertThat(coordinator.possuiExecucaoEmVoo()).isFalse();
        });
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
    }
    private Reply get(String path) throws Exception { return reply(http.send(request(path), HttpResponse.BodyHandlers.ofByteArray())); }
    private CompletableFuture<HttpResponse<byte[]>> async(String path) { return http.sendAsync(request(path), HttpResponse.BodyHandlers.ofByteArray()); }
    private Reply reply(HttpResponse<byte[]> value) throws Exception {
        String raw = new String(value.body(), StandardCharsets.UTF_8);
        return new Reply(value.statusCode(), json.readTree(raw), raw);
    }
    private static List<String> ids(JsonNode array) { List<String> ids = new ArrayList<>(); array.forEach(item -> ids.add(item.path("id").asText())); return ids; }
    private static JsonNode find(JsonNode array, UUID id) {
        for (JsonNode item : array) if (id.toString().equals(item.path("id").asText())) return item;
        throw new AssertionError("Expected synthetic id absent: " + id);
    }
    private static String previewKey(UUID file) {
        try {
            byte[] input = (file + ":sem-checksum:v1").getBytes(StandardCharsets.UTF_8);
            return "hml/publicas/restritas-borradas/v1/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)).substring(0, 32) + ".jpg";
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private record Media(UUID id, UUID file, int order, String originalKey, String previewKey) { }
    private record Ad(UUID id, UUID user, String slug, List<Media> media) { }
    private record Fixture(List<Ad> ads, OffsetDateTime lastMediaUpdate) { }
    private record Reply(int status, JsonNode body, String raw) { }

    static final class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            new LocalidadesConsultaPostgres17IntegrationTest.PostgresInitializer().initialize(context);
            try { remote = new LocalS3(); } catch (IOException error) { throw new IllegalStateException(error); }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("server.address", "127.0.0.1");
            // Exercise a retained request EntityManager, not merely the easier OSIV=false case.
            // This test-only override makes the second transactional read prove freshness.
            p.put("spring.jpa.open-in-view", true);
            p.put("spring.sql.init.mode", "never");
            p.put("app.env", "local");
            p.put("app.canonical-domain", "https://topsdojob.com");
            p.put("app.auth.hml-code-vault-enabled", false);
            p.put("app.outbox.email.enabled", false);
            p.put("app.outbox.email.encryption-key", "YWFhYWFhYWFhYWFhYWFhYQ==");
            p.put("app.event.hash-salt", "synthetic-routes-hash-salt");
            p.put("app.age-gate.signing-value", "synthetic-routes-age-signing-value");
            p.put("efi.pix.enabled", false);
            p.put("efi.pix.reconciliation-enabled", false);
            p.put("efi.pix.webhook-registration-enabled", false);
            p.put("app.storage.r2.endpoint", remote.endpoint());
            p.put("app.storage.r2.access-key", "synthetic-access");
            p.put("app.storage.r2.signing-value", "synthetic-signing-value");
            p.put("app.storage.r2.document-bucket", "documentos-teste");
            p.put("app.storage.r2.document-prefix", "hml/documentos/");
            p.put("app.storage.r2.public-base-url", "https://public.example.invalid");
            p.put("app.storage.r2.preserved-public-media-bucket", "");
            p.put("app.storage.r2.preserved-public-media-prefix", "");
            p.put("app.storage.r2.preserved-public-base-url", "");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("routes-preview-synthetic", p));
            context.addBeanFactoryPostProcessor(factory -> {
                String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (factory instanceof BeanDefinitionRegistry registry && registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            });
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean @Primary @ConfigurationProperties(prefix = "app.storage.r2")
        R2StorageProperties localR2Properties() { return new LocalProperties(); }
        @Bean FilterRegistrationBean<Filter> observeActualHttpCompletion() {
            FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>((request, response, chain) -> {
                HTTP_ACTIVE.incrementAndGet();
                try { chain.doFilter(request, response); } finally { HTTP_ACTIVE.decrementAndGet(); }
            });
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return bean;
        }
    }

    /** Exact synthetic HTTP endpoint exception; production SigV4 transport remains unchanged. */
    static class LocalProperties extends R2StorageProperties {
        @Override public void validateConfigured() {
            if (!remote.endpoint().equals(getEndpoint()) || !"synthetic-access".equals(getAccessKey())
                    || !"synthetic-signing-value".equals(getSigningValue())) {
                throw new IllegalStateException("Synthetic R2 binding escaped loopback isolation");
            }
            String loopback = getEndpoint();
            setEndpoint(loopback.replace("http:", "https:"));
            try { super.validateConfigured(); } finally { setEndpoint(loopback); }
        }
    }

    private static final class LocalS3 implements AutoCloseable {
        final HttpServer server;
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16));
        final Set<String> keys = java.util.concurrent.ConcurrentHashMap.newKeySet();
        final AtomicInteger heads = new AtomicInteger(), lists = new AtomicInteger(), forbidden = new AtomicInteger(), active = new AtomicInteger(), maxPool = new AtomicInteger(), maxWaiting = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile HikariDataSource pool;
        volatile int status = 200;
        volatile CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(0);
        volatile String gatedKey;
        LocalS3() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }
        String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void reset(HikariDataSource dataSource) {
            pool = dataSource; keys.clear(); heads.set(0); lists.set(0); forbidden.set(0);
            maxPool.set(0); maxWaiting.set(0); failure.set(null); status = 200;
            entered = new CountDownLatch(1); release = new CountDownLatch(0); gatedKey = null;
        }
        void hold() { entered = new CountDownLatch(1); release = new CountDownLatch(1); }
        void samplePool() {
            maxPool.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
            maxWaiting.accumulateAndGet(pool.getHikariPoolMXBean().getThreadsAwaitingConnection(), Math::max);
        }
        void handle(HttpExchange exchange) throws IOException {
            active.incrementAndGet();
            try {
                assertThat(java.util.Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization"))
                        .map(value -> value.startsWith("AWS4-HMAC-SHA256 ")).orElse(false))
                        .as("real signed R2 HTTP").isTrue();
                assertThat(exchange.getRequestHeaders().getFirst("x-amz-date")).isNotBlank();
                if (!exchange.getRequestMethod().equals("HEAD")) {
                    if (exchange.getRequestMethod().equals("GET")) lists.incrementAndGet(); else forbidden.incrementAndGet();
                    throw new AssertionError("Route must never read bodies, list objects or mutate storage");
                }
                String prefix = "/publicas-teste/";
                assertThat(exchange.getRequestURI().getPath()).startsWith(prefix);
                String key = exchange.getRequestURI().getPath().substring(prefix.length());
                assertThat(keys).contains(key);
                heads.incrementAndGet(); samplePool();
                if (release.getCount() != 0 && gatedKey == null) {
                    gatedKey = key; entered.countDown();
                    if (!release.await(8, TimeUnit.SECONDS)) throw new AssertionError("Synthetic HEAD gate was not released");
                }
                Thread.sleep(125); // Synthetic latency, not a historical R2 measurement.
                samplePool();
                exchange.sendResponseHeaders(status, -1);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                try { exchange.sendResponseHeaders(500, -1); } catch (IOException ignored) { }
            } finally { exchange.close(); active.decrementAndGet(); }
        }
        public void close() throws Exception {
            release.countDown(); server.stop(0); executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic S3 workers did not stop");
        }
    }
}
