package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.platform.error.ApiErrorCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class EfiPagamentoRecuperacaoServiceTest {

    private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PlanoCreditoRepository planoRepository = mock(PlanoCreditoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final PagamentoEventoRepository pagamentoEventoRepository = mock(PagamentoEventoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final EfiPixGateway gateway = mock(EfiPixGateway.class);
    private final EfiPagamentoConciliacaoService conciliacaoService = mock(EfiPagamentoConciliacaoService.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final EfiPagamentoService service = new EfiPagamentoService(
            usuarioService,
            usuarioRepository,
            planoRepository,
            pagamentoRepository,
            pagamentoEventoRepository,
            auditoriaRepository,
            gateway,
            conciliacaoService,
            rateLimiter,
            transactionManager);

    @BeforeEach
    void setUp() {
        when(gateway.ambiente()).thenReturn(AmbientePagamento.SANDBOX);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void recuperaCobrancaRemotaDepoisDeFalhaNaFinalizacaoLocal() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = plano(planoId);
        AtomicReference<PagamentoEntity> persistido = new AtomicReference<>();
        AtomicBoolean falharFinalizacao = new AtomicBoolean(true);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(ignored -> Optional.ofNullable(persistido.get()));
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PagamentoEntity salvo = invocation.getArgument(0);
            persistido.set(salvo);
            return salvo;
        });
        when(pagamentoRepository.findByTxidForUpdate(anyString())).thenAnswer(ignored -> {
            if (falharFinalizacao.getAndSet(false)) {
                throw new IllegalStateException("falha local sintetica");
            }
            return Optional.ofNullable(persistido.get());
        });
        when(gateway.criarCobranca(anyString(), any(), anyString())).thenAnswer(invocation -> cobranca(
                invocation.getArgument(0),
                new BigDecimal("5.00")));
        when(gateway.consultarCobranca(anyString())).thenAnswer(invocation -> cobranca(
                invocation.getArgument(0),
                new BigDecimal("5.00")));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-recuperacao-local",
                authentication,
                "request-recuperacao-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha local sintetica");

        String txidOriginal = persistido.get().getTxid();
        var recuperado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-recuperacao-local",
                authentication,
                "request-recuperacao-02");

        assertThat(recuperado.idempotente()).isTrue();
        assertThat(persistido.get().getTxid()).isEqualTo(txidOriginal);
        assertThat(persistido.get().getStatusInterno()).isEqualTo(StatusInternoPagamento.AGUARDANDO_PAGAMENTO);
        verify(pagamentoRepository, times(1)).saveAndFlush(any());
        verify(gateway, times(1)).criarCobranca(eq(txidOriginal), any(), anyString());
        verify(gateway, times(1)).consultarCobranca(txidOriginal);
    }

    @Test
    void retryApos404TransitorioReenviaPutComMesmoTxidSemDuplicarCobrancaLogica() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = plano(planoId);
        AtomicReference<PagamentoEntity> persistido = new AtomicReference<>();
        AtomicBoolean falharFinalizacao = new AtomicBoolean(true);
        AtomicBoolean consultaAindaInconsistente = new AtomicBoolean(true);
        Map<String, EfiPixGateway.CobrancaPix> cobrancasRemotas = new HashMap<>();
        List<String> txidsEnviadosAoPut = new ArrayList<>();

        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.findByIdempotencyKey(anyString()))
                .thenAnswer(ignored -> Optional.ofNullable(persistido.get()));
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PagamentoEntity salvo = invocation.getArgument(0);
            persistido.set(salvo);
            return salvo;
        });
        when(pagamentoRepository.findByTxidForUpdate(anyString())).thenAnswer(ignored -> {
            if (falharFinalizacao.getAndSet(false)) {
                throw new IllegalStateException("falha local apos criacao remota");
            }
            return Optional.ofNullable(persistido.get());
        });
        when(gateway.criarCobranca(anyString(), any(), anyString())).thenAnswer(invocation -> {
            String txid = invocation.getArgument(0);
            txidsEnviadosAoPut.add(txid);
            return cobrancasRemotas.computeIfAbsent(
                    txid,
                    chave -> cobranca(chave, new BigDecimal("5.00")));
        });
        when(gateway.consultarCobranca(anyString())).thenAnswer(invocation -> {
            if (consultaAindaInconsistente.getAndSet(false)) {
                throw new EfiPixGatewayException("404 transitorio", false, 404);
            }
            return cobrancasRemotas.get(invocation.<String>getArgument(0));
        });

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-404-transitorio",
                authentication,
                "request-recuperacao-404-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha local apos criacao remota");

        String txidOriginal = persistido.get().getTxid();
        var recuperado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-404-transitorio",
                authentication,
                "request-recuperacao-404-02");

        assertThat(recuperado.idempotente()).isTrue();
        assertThat(persistido.get().getTxid()).isEqualTo(txidOriginal);
        assertThat(persistido.get().getStatusInterno())
                .isEqualTo(StatusInternoPagamento.AGUARDANDO_PAGAMENTO);
        assertThat(cobrancasRemotas).containsOnlyKeys(txidOriginal);
        assertThat(txidsEnviadosAoPut).containsExactly(txidOriginal, txidOriginal);
        verify(pagamentoRepository, times(1)).saveAndFlush(any());
        verify(gateway, times(1)).consultarCobranca(txidOriginal);
        verify(gateway, times(2)).criarCobranca(eq(txidOriginal), any(), anyString());
    }

    @Test
    void criaRemotamenteSomenteDepoisDeConsultaConfirmar404() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = pagamento(usuarioId, planoId, "pix-recovery-missing-remote-01");
        when(pagamentoRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existente));
        when(pagamentoRepository.findByTxidForUpdate(existente.getTxid())).thenReturn(Optional.of(existente));
        when(gateway.consultarCobranca(existente.getTxid()))
                .thenThrow(new EfiPixGatewayException("cobranca ausente", false, 404));
        when(gateway.criarCobranca(eq(existente.getTxid()), any(), anyString()))
                .thenReturn(cobranca(existente.getTxid(), new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-remoto-ausente",
                authentication);

        assertThat(resultado.idempotente()).isTrue();
        InOrder ordem = inOrder(gateway);
        ordem.verify(gateway).consultarCobranca(existente.getTxid());
        ordem.verify(gateway).criarCobranca(eq(existente.getTxid()), any(), anyString());
    }

    @Test
    void falhaTransitoriaNaoCriaSegundaCobranca() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = pagamento(usuarioId, planoId, "pix-recovery-transient-error-01");
        when(pagamentoRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existente));
        when(gateway.consultarCobranca(existente.getTxid()))
                .thenThrow(new EfiPixGatewayException("indisponivel", false, 503));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-falha-transitoria",
                authentication,
                "request-recuperacao-03"))
                .isInstanceOf(PagamentoPixException.class)
                .hasMessage("Não foi possível iniciar a cobrança Pix agora. Tente novamente.")
                .satisfies(erro -> assertThat(((PagamentoPixException) erro).code())
                        .isEqualTo(ApiErrorCode.PIX_CRIACAO_INDISPONIVEL));

        verify(gateway, never()).criarCobranca(anyString(), any(), anyString());
        verify(conciliacaoService).registrarFalhaTransitoria(
                existente.getTxid(),
                "request-recuperacao-03");
    }

    @Test
    void pagamentoTerminalNaoConsultaNemCriaNoProvider() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = pagamento(usuarioId, planoId, "pix-recovery-terminal-test-01");
        existente.atualizarStatusProvedor(
                StatusInternoPagamento.EXPIRADO,
                "EXPIRADA",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(existente));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-terminal",
                authentication);

        assertThat(resultado.status()).isEqualTo("EXPIRADO");
        verify(gateway, never()).consultarCobranca(anyString());
        verify(gateway, never()).criarCobranca(anyString(), any(), anyString());
    }

    private PlanoCreditoEntity plano(UUID planoId) {
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(plano.getId()).thenReturn(planoId);
        when(plano.getAtivo()).thenReturn(true);
        when(plano.getValor()).thenReturn(new BigDecimal("5.00"));
        when(plano.getQuantidadeCreditos()).thenReturn(50);
        when(plano.getMoeda()).thenReturn("BRL");
        when(plano.getNome()).thenReturn("50 creditos");
        return plano;
    }

    private PagamentoEntity pagamento(UUID usuarioId, UUID planoId, String txid) {
        return PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                txid,
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":fixture",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private EfiPixGateway.CobrancaPix cobranca(String txid, BigDecimal valor) {
        return new EfiPixGateway.CobrancaPix(
                AmbientePagamento.SANDBOX,
                txid,
                "ATIVA",
                "123",
                valor,
                BigDecimal.ZERO,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                "pix-sintetico",
                "data:image/svg+xml;base64,c2ludGV0aWNv");
    }
}
