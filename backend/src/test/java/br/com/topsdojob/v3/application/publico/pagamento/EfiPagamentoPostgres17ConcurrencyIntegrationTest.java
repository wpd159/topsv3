package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.security.publico.PublicUserPrincipal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.env=homologacao",
                "app.event.hash-salt=hash-fixture",
                "app.age-gate.signing-value=age-gate-runtime-test-value",
                "app.outbox.email.enabled=false",
                "app.storage.r2.enabled=false",
                "efi.pix.enabled=true",
                "efi.pix.reconciliation-enabled=true",
                "efi.pix.reconciliation-retry-backoff-seconds=1",
                "efi.pix.reconciliation-batch-size=25",
                "efi.pix.reconciliation-max-per-cycle=25",
                "efi.pix.webhook-registration-enabled=false",
                "efi.pix.webhook-verifier=webhook-test-value",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.task.scheduling.enabled=false",
                "spring.datasource.hikari.maximum-pool-size=8"
        })
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "EFI_PAGAMENTO_POSTGRES17_ENABLED", matches = "true")
class EfiPagamentoPostgres17ConcurrencyIntegrationTest {

    private static final String WEBHOOK_VERIFIER = "webhook-test-value";
    private static final String TXID_RETRY = "RetryRuntimePayment00000000001";
    private static final String EVENTO_RETRY = "RetryEventRuntime00000001";
    private static final String TXID_CONCORRENTE = "ConcurrentRuntimePayment000001";
    private static final String EVENTO_CONCORRENTE = "ConcurrentEventRuntime0001";
    private static final String TXID_RECONCILIACAO = "SchedulerRuntimePayment00000001";
    private static final String TXID_LEGADO = "LegacyRuntimePayment0000000001";
    private static final String EVENTO_LEGADO = "LegacyEventRuntime0000001";
    private static final Postgres17Fixture POSTGRES = Postgres17Fixture.start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EfiPagamentoConciliacaoService conciliacaoService;

    @Autowired
    private EfiPagamentoService pagamentoService;

    @Autowired
    private EfiPagamentoReconciliacaoScheduler reconciliacaoScheduler;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private EfiPixGateway gateway;
    @MockBean
    private ObjectStorage objectStorage;


    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::username);
        registry.add("spring.datasource.password", POSTGRES::credential);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE usuario, plano_credito CASCADE");
        when(gateway.ambiente()).thenReturn(AmbientePagamento.SANDBOX);
    }

    @AfterAll
    static void closePostgres() {
        POSTGRES.close();
    }

    @Test
    void webhookPersistidoPodeSerReprocessadoAposFalhaDoProvider() throws Exception {
        Scenario scenario = seed(TXID_RETRY, "RETRY", "SANDBOX");
        when(gateway.consultarCobranca(TXID_RETRY))
                .thenThrow(new EfiPixGatewayException("provider indisponivel", false))
                .thenReturn(confirmada(TXID_RETRY));

        enviar(TXID_RETRY, EVENTO_RETRY, "request-runtime-retry-01")
                .andExpect(status().isBadGateway());

        assertThat(count("pagamento_webhook", "evento_id", EVENTO_RETRY)).isEqualTo(1L);
        assertThat(webhookResultado(EVENTO_RETRY)).isEqualTo("ERRO");
        assertThat(webhookTentativas(EVENTO_RETRY)).isEqualTo(1);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isZero();
        assertThat(count("pagamento_evento", "pagamento_id", scenario.pagamentoId())).isZero();

        enviar(TXID_RETRY, EVENTO_RETRY, "request-runtime-retry-02")
                .andExpect(status().isOk());
        enviar(TXID_RETRY, EVENTO_RETRY, "request-runtime-retry-03")
                .andExpect(status().isOk());

        assertThat(webhookResultado(EVENTO_RETRY)).isEqualTo("PROCESSADO");
        assertThat(webhookTentativas(EVENTO_RETRY)).isEqualTo(3);
        assertCreditoUnico(scenario, EVENTO_RETRY);
        verify(gateway, times(2)).consultarCobranca(TXID_RETRY);
    }

    @Test
    void notificacoesConcorrentesUsamControllerServicosERepositoriosReais() throws Exception {
        Scenario scenario = seed(TXID_CONCORRENTE, "CONCORRENTE", "SANDBOX");
        when(gateway.consultarCobranca(TXID_CONCORRENTE))
                .thenReturn(confirmada(TXID_CONCORRENTE));

        List<Integer> statuses = concurrently(
                () -> enviar(TXID_CONCORRENTE, EVENTO_CONCORRENTE, "request-runtime-concurrent-01")
                        .andReturn().getResponse().getStatus(),
                () -> enviar(TXID_CONCORRENTE, EVENTO_CONCORRENTE, "request-runtime-concurrent-02")
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 200);
        assertThat(count("pagamento_webhook", "evento_id", EVENTO_CONCORRENTE)).isEqualTo(1L);
        assertThat(webhookResultado(EVENTO_CONCORRENTE)).isEqualTo("PROCESSADO");
        assertThat(webhookTentativas(EVENTO_CONCORRENTE)).isEqualTo(2);
        assertCreditoUnico(scenario, EVENTO_CONCORRENTE);
        verify(gateway, atLeastOnce()).consultarCobranca(TXID_CONCORRENTE);
    }

    @Test
    void reconciliacaoAutomaticaConcorrenteUsaLockFisicoECreditaUmaVez() throws Exception {
        Scenario scenario = seed(TXID_RECONCILIACAO, "RECONCILIACAO", "SANDBOX");
        when(gateway.consultarCobranca(TXID_RECONCILIACAO))
                .thenAnswer(ignored -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return confirmada(TXID_RECONCILIACAO);
                });

        List<Integer> resultados = concurrently(
                () -> conciliacaoService.conciliar(
                                TXID_RECONCILIACAO,
                                null,
                                null,
                                br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                                "request-runtime-scheduler-01")
                        .idempotente() ? 1 : 0,
                () -> conciliacaoService.conciliar(
                                TXID_RECONCILIACAO,
                                null,
                                null,
                                br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento.CONSULTA_PROVEDOR,
                                "request-runtime-scheduler-02")
                        .idempotente() ? 1 : 0);

        assertThat(resultados).containsExactlyInAnyOrder(0, 1);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isEqualTo(1L);
        assertThat(count("pagamento_conciliacao", "pagamento_id", scenario.pagamentoId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM pagamento
                WHERE id = ?
                  AND status_interno = 'APROVADO'
                  AND creditado_em IS NOT NULL
                """,
                Long.class,
                scenario.pagamentoId())).isEqualTo(1L);
        assertThat(count("ativacao_beneficio", "usuario_id", scenario.usuarioId())).isZero();
        assertThat(count("anuncio", "usuario_id", scenario.usuarioId())).isZero();
        assertThat(count("story_anuncio", "criado_por", scenario.usuarioId())).isZero();
        verify(gateway, atLeastOnce()).consultarCobranca(TXID_RECONCILIACAO);
    }
    @Test
    void checkoutESchedulerConcorremSemDuplicarCobrancaOuCredito() throws Exception {
        Scenario scenario = seedUsuarioPlano("CHECKOUT_SCHEDULER");
        CountDownLatch cobrancaRemotaCriada = new CountDownLatch(1);
        CountDownLatch liberarCheckout = new CountDownLatch(1);
        AtomicReference<String> txidRemoto = new AtomicReference<>();
        AtomicReference<EfiPixGateway.CobrancaPix> cobrancaRemota = new AtomicReference<>();

        when(gateway.criarCobranca(anyString(), any(), anyString())).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            String txid = invocation.getArgument(0);
            txidRemoto.set(txid);
            cobrancaRemota.set(confirmada(txid));
            cobrancaRemotaCriada.countDown();
            if (!liberarCheckout.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("checkout concorrente nao foi liberado");
            }
            return cobrancaRemota.get();
        });
        when(gateway.consultarCobranca(anyString())).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            assertThat(invocation.<String>getArgument(0)).isEqualTo(txidRemoto.get());
            return cobrancaRemota.get();
        });

        var authentication = new UsernamePasswordAuthenticationToken(
                new PublicUserPrincipal(
                        scenario.usuarioId(),
                        "pagamento-qa",
                        "pagamento.qa@example.invalid"),
                null,
                List.of());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var checkout = executor.submit(() -> pagamentoService.criar(
                    new EfiPixCheckoutRequest(scenario.planoId()),
                    "checkout-scheduler-race",
                    authentication,
                    "request-checkout-scheduler-race"));

            assertThat(cobrancaRemotaCriada.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(1_200L);
            assertThat(reconciliacaoScheduler.processarCiclo()).isEqualTo(1);

            liberarCheckout.countDown();
            var resposta = checkout.get(10, TimeUnit.SECONDS);
            assertThat(resposta.status()).isEqualTo("APROVADO");
        } finally {
            liberarCheckout.countDown();
            executor.shutdownNow();
        }

        UUID pagamentoId = jdbc.queryForObject(
                "SELECT id FROM pagamento WHERE usuario_id = ? AND plano_credito_id = ?",
                UUID.class,
                scenario.usuarioId(),
                scenario.planoId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pagamento WHERE usuario_id = ? AND plano_credito_id = ?",
                Long.class,
                scenario.usuarioId(),
                scenario.planoId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT txid) FROM pagamento WHERE usuario_id = ? AND plano_credito_id = ?",
                Long.class,
                scenario.usuarioId(),
                scenario.planoId())).isEqualTo(1L);
        assertThat(count("movimento_credito", "referencia_id", pagamentoId)).isEqualTo(1L);
        assertThat(count("pagamento_conciliacao", "pagamento_id", pagamentoId)).isEqualTo(1L);
        verify(gateway, times(1)).criarCobranca(
                org.mockito.ArgumentMatchers.eq(txidRemoto.get()),
                any(),
                anyString());
        verify(gateway, atLeastOnce()).consultarCobranca(txidRemoto.get());
    }

    @Test
    void duasInstanciasDoSchedulerProcessamMesmoPagamentoSemDuplicarCredito() throws Exception {
        String txid = "TwoSchedulersRuntimePayment00001";
        Scenario scenario = seed(txid, "DOIS_SCHEDULERS", "SANDBOX");
        jdbc.update(
                "UPDATE pagamento SET atualizado_em = now() - interval '10 minutes' WHERE id = ?",
                scenario.pagamentoId());
        when(gateway.consultarCobranca(txid)).thenAnswer(ignored -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            return confirmada(txid);
        });

        List<Integer> resultados = concurrently(
                reconciliacaoScheduler::processarCiclo,
                reconciliacaoScheduler::processarCiclo);

        assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).isBetween(0, 1));
        assertThat(resultados.stream().mapToInt(Integer::intValue).sum()).isBetween(1, 2);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isEqualTo(1L);
        assertThat(count("pagamento_conciliacao", "pagamento_id", scenario.pagamentoId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT status_interno FROM pagamento WHERE id = ?",
                String.class,
                scenario.pagamentoId())).isEqualTo("APROVADO");
        verify(gateway, atLeastOnce()).consultarCobranca(txid);
    }

    @Test
    void erroNaoTransitorioNaoEhSelecionadoPeloScheduler() {
        String txid = "PermanentErrorRuntimePayment0001";
        Scenario scenario = seed(txid, "ERRO_PERMANENTE", "SANDBOX");
        jdbc.update(
                """
                UPDATE pagamento
                   SET status_interno = 'ERRO',
                       status_provedor = 'VALOR_DIVERGENTE',
                       atualizado_em = now() - interval '10 minutes'
                 WHERE id = ?
                """,
                scenario.pagamentoId());

        assertThat(reconciliacaoScheduler.processarCiclo()).isZero();

        verify(gateway, never()).consultarCobranca(txid);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isZero();
    }

    @Test
    void indisponibilidadeTransitoriaAplicaBackoffAntesDoRetry() {
        String txid = "TransientBackoffRuntimePayment01";
        Scenario scenario = seed(txid, "BACKOFF", "SANDBOX");
        jdbc.update(
                "UPDATE pagamento SET atualizado_em = now() - interval '10 minutes' WHERE id = ?",
                scenario.pagamentoId());
        when(gateway.consultarCobranca(txid))
                .thenThrow(new EfiPixGatewayException("provider indisponivel", false, 503));

        assertThat(reconciliacaoScheduler.processarCiclo()).isEqualTo(1);
        assertThat(reconciliacaoScheduler.processarCiclo()).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT status_interno FROM pagamento WHERE id = ?",
                String.class,
                scenario.pagamentoId())).isEqualTo("ERRO");
        assertThat(jdbc.queryForObject(
                "SELECT status_provedor FROM pagamento WHERE id = ?",
                String.class,
                scenario.pagamentoId()))
                .isEqualTo(EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isZero();
        verify(gateway, times(1)).consultarCobranca(txid);
    }


    @Test
    void pagamentoHistoricoSemAmbienteNuncaEhCreditadoAutomaticamente() throws Exception {
        Scenario scenario = seed(TXID_LEGADO, "LEGADO", null);
        when(gateway.consultarCobranca(TXID_LEGADO))
                .thenReturn(confirmada(TXID_LEGADO));

        enviar(TXID_LEGADO, EVENTO_LEGADO, "request-runtime-legacy-01")
                .andExpect(status().isBadGateway());

        assertThat(webhookResultado(EVENTO_LEGADO)).isEqualTo("ERRO");
        assertThat(jdbc.queryForObject(
                "SELECT status_provedor FROM pagamento WHERE id = ?",
                String.class,
                scenario.pagamentoId())).isEqualTo("AMBIENTE_LEGADO_INDEFINIDO");
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isZero();
        assertThat(count("pagamento_conciliacao", "pagamento_id", scenario.pagamentoId())).isZero();
        verify(gateway, never()).consultarCobranca(TXID_LEGADO);
    }

    private org.springframework.test.web.servlet.ResultActions enviar(
            String txid,
            String eventoId,
            String requestId) throws Exception {
        return mockMvc.perform(post("/api/public/webhooks/efi/pix")
                .queryParam("hmac", WEBHOOK_VERIFIER)
                .header("X-Real-IP", "127.0.0.1")
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(txid, eventoId)));
    }

    private String payload(String txid, String eventoId) {
        return """
                {"pix":[{"endToEndId":"%s","txid":"%s","valor":"5.00",
                "horario":"2026-08-08T12:00:00Z"}]}
                """.formatted(eventoId, txid);
    }

    private EfiPixGateway.CobrancaPix confirmada(String txid) {
        return new EfiPixGateway.CobrancaPix(
                AmbientePagamento.SANDBOX,
                txid,
                "CONCLUIDA",
                "loc-" + txid,
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                null,
                null);
    }

    private Scenario seedUsuarioPlano(String suffix) {
        Scenario scenario = new Scenario(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
                VALUES (?, 'Pagamento QA', 'ATIVO', 'ANUNCIANTE', now(), now(), 0)
                """, scenario.usuarioId());
        jdbc.update("""
                INSERT INTO plano_credito (
                  id, codigo, nome, quantidade_creditos, valor, moeda, ativo,
                  criado_em, atualizado_em, descricao, ordem_exibicao
                ) VALUES (?, ?, 'Pacote QA', 50, 5.00, 'BRL', true,
                          now(), now(), 'Teste runtime', 0)
                """, scenario.planoId(), "PACOTE_QA_" + suffix);
        return scenario;
    }

    private Scenario seed(String txid, String suffix, String ambiente) {
        Scenario scenario = new Scenario(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO usuario (id, nome, status, tipo_conta, criado_em, atualizado_em, versao)
                VALUES (?, 'Pagamento QA', 'ATIVO', 'ANUNCIANTE', now(), now(), 0)
                """, scenario.usuarioId());
        jdbc.update("""
                INSERT INTO plano_credito (
                  id, codigo, nome, quantidade_creditos, valor, moeda, ativo,
                  criado_em, atualizado_em, descricao, ordem_exibicao
                ) VALUES (?, ?, 'Pacote QA', 50, 5.00, 'BRL', true,
                          now(), now(), 'Teste runtime', 0)
                """, scenario.planoId(), "PACOTE_QA_" + suffix);
        jdbc.update("""
                INSERT INTO pagamento (
                  id, usuario_id, plano_credito_id, provedor, metodo, ambiente, txid,
                  identificador_provedor, valor, moeda, quantidade_creditos,
                  status_interno, status_provedor, expiracao_em,
                  idempotency_key, criado_em, atualizado_em
                ) VALUES (?, ?, ?, 'EFI', 'PIX', ?, ?, ?, 5.00, 'BRL', 50,
                          'AGUARDANDO_PAGAMENTO', 'ATIVA', now() + interval '1 hour',
                          ?, now(), now())
                """,
                scenario.pagamentoId(),
                scenario.usuarioId(),
                scenario.planoId(),
                ambiente,
                txid,
                "loc-" + txid,
                "efi-checkout:runtime:" + suffix.toLowerCase());
        return scenario;
    }

    private void assertCreditoUnico(Scenario scenario, String eventoId) {
        assertThat(count("pagamento_evento", "provedor_evento_id", eventoId)).isEqualTo(1L);
        assertThat(count("movimento_credito", "referencia_id", scenario.pagamentoId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM movimento_credito
                WHERE referencia_id = ?
                  AND tipo = 'ENTRADA'
                  AND direcao = 'CREDITO'
                  AND origem = 'PAGAMENTO'
                """,
                Long.class,
                scenario.pagamentoId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM pagamento
                WHERE id = ?
                  AND ambiente = 'SANDBOX'
                  AND status_interno = 'APROVADO'
                  AND creditado_em IS NOT NULL
                """,
                Long.class,
                scenario.pagamentoId())).isEqualTo(1L);
        assertThat(count("ativacao_beneficio", "usuario_id", scenario.usuarioId())).isZero();
        assertThat(count("anuncio", "usuario_id", scenario.usuarioId())).isZero();
        assertThat(count("story_anuncio", "criado_por", scenario.usuarioId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version::integer = 51 AND success",
                Long.class)).isEqualTo(1L);
    }

    private long count(String table, String column, Object value) {
        if (!List.of(
                "pagamento_webhook",
                "pagamento_evento",
                "movimento_credito",
                "pagamento_conciliacao",
                "ativacao_beneficio",
                "anuncio",
                "story_anuncio").contains(table)
                || !List.of(
                "evento_id",
                "provedor_evento_id",
                "pagamento_id",
                "referencia_id",
                "usuario_id",
                "criado_por").contains(column)) {
            throw new IllegalArgumentException("assert SQL fora da allowlist do teste");
        }
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                value);
    }

    private String webhookResultado(String eventoId) {
        return jdbc.queryForObject(
                "SELECT resultado FROM pagamento_webhook WHERE evento_id = ?",
                String.class,
                eventoId);
    }

    private int webhookTentativas(String eventoId) {
        return jdbc.queryForObject(
                "SELECT tentativas FROM pagamento_webhook WHERE evento_id = ?",
                Integer.class,
                eventoId);
    }

    private static List<Integer> concurrently(
            Callable<Integer> first,
            Callable<Integer> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> wrapFirst = () -> {
                ready.countDown();
                start.await();
                return first.call();
            };
            Callable<Integer> wrapSecond = () -> {
                ready.countDown();
                start.await();
                return second.call();
            };
            var firstResult = executor.submit(wrapFirst);
            var secondResult = executor.submit(wrapSecond);
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private record Scenario(UUID usuarioId, UUID planoId, UUID pagamentoId) {
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
            String network = "topsv3-efi-runtime-" + suffix + "-net";
            String container = "topsv3-efi-runtime-" + suffix;
            String username = "topsv3test";
            String credential = UUID.randomUUID().toString() + UUID.randomUUID();
            try {
                command("docker", "network", "create", network);
                command(
                        Map.of("POSTGRES_PASSWORD", credential),
                        "docker", "run", "--pull=never", "-d", "--name", container,
                        "--network", network,
                        "-p", "127.0.0.1::5432",
                        "-e", "POSTGRES_DB=topsv3_efi",
                        "-e", "POSTGRES_USER=" + username,
                        "-e", "POSTGRES_PASSWORD",
                        "postgres:17-alpine");
                awaitPostgres(container, username, credential);
                migrate(network, container, username, credential, "migrate");
                migrate(network, container, username, credential, "validate");
                migrate(network, container, username, credential, "migrate");
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
            return "jdbc:postgresql://127.0.0.1:" + port + "/topsv3_efi";
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
                    "-url=jdbc:postgresql://" + container + ":5432/topsv3_efi",
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
                        "--username", username, "--dbname", "topsv3_efi") == 0) {
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
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException(args[0] + " falhou");
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
