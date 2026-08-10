package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.auth.PublicAuthRateLimiter;
import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class EfiPagamentoCancelamentoServiceTest {

    private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PlanoCreditoRepository planoRepository = mock(PlanoCreditoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final PagamentoEventoRepository eventoRepository = mock(PagamentoEventoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final EfiPixGateway gateway = mock(EfiPixGateway.class);
    private final EfiPagamentoConciliacaoService conciliacaoService =
            mock(EfiPagamentoConciliacaoService.class);
    private final PublicAuthRateLimiter rateLimiter = mock(PublicAuthRateLimiter.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final Authentication authentication = mock(Authentication.class);
    private final UsuarioEntity usuario = mock(UsuarioEntity.class);
    private final UUID usuarioId = UUID.randomUUID();

    private final EfiPagamentoService service = new EfiPagamentoService(
            usuarioService,
            usuarioRepository,
            planoRepository,
            pagamentoRepository,
            eventoRepository,
            auditoriaRepository,
            gateway,
            conciliacaoService,
            rateLimiter,
            transactionManager);

    @BeforeEach
    void setUp() {
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(gateway.ambiente()).thenReturn(AmbientePagamento.SANDBOX);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void cancelaRemotamenteAntesDeSincronizarLocalmente() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        EfiPixGateway.CobrancaPix ativa = cobranca(pagamento, "ATIVA", BigDecimal.ZERO);
        EfiPixGateway.CobrancaPix removida =
                cobranca(pagamento, "REMOVIDA_PELO_USUARIO_RECEBEDOR", BigDecimal.ZERO);
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid())).thenReturn(ativa);
        when(gateway.cancelarCobranca(pagamento.getTxid())).thenReturn(removida);
        sincronizarComo(pagamento, StatusInternoPagamento.CANCELADO);

        var resultado = service.cancelar(
                pagamento.getId(),
                "cancelamento-ativo",
                authentication,
                "req-cancelamento-ativo");

        assertThat(resultado.status()).isEqualTo("CANCELADO");
        assertThat(resultado.cancelavel()).isFalse();
        assertThat(resultado.ambiente()).isEqualTo("HOMOLOGACAO");
        InOrder ordem = inOrder(gateway, conciliacaoService);
        ordem.verify(gateway).consultarCobrancaSemQrCode(pagamento.getTxid());
        ordem.verify(gateway).cancelarCobranca(pagamento.getTxid());
        ordem.verify(conciliacaoService).aplicarCobrancaConsultada(
                any(), any(), any(), any(), any());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void pagamentoAlheioEhRejeitadoAntesDoGateway() {
        UUID pagamentoId = UUID.randomUUID();
        when(pagamentoRepository.findByIdAndUsuarioIdForUpdate(pagamentoId, usuarioId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(
                pagamentoId,
                "cancelamento-alheio",
                authentication,
                "req-cancelamento-alheio"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        verify(gateway, never()).consultarCobrancaSemQrCode(any());
        verify(gateway, never()).cancelarCobranca(any());
    }

    @Test
    void pagamentoConcluidoEhConciliadoERecusaCancelamento() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        EfiPixGateway.CobrancaPix concluida =
                cobranca(pagamento, "CONCLUIDA", pagamento.getValor());
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid())).thenReturn(concluida);
        when(conciliacaoService.aplicarCobrancaConsultada(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    pagamento.marcarAprovadoECreditado(
                            OffsetDateTime.now(ZoneOffset.UTC),
                            OffsetDateTime.now(ZoneOffset.UTC));
                    return new EfiPagamentoConciliacaoService.ConciliacaoResultado(
                            pagamento,
                            invocation.getArgument(0),
                            false,
                            null);
                });

        assertThatThrownBy(() -> service.cancelar(
                pagamento.getId(),
                "cancelamento-concluido",
                authentication,
                "req-cancelamento-concluido"))
                .isInstanceOfSatisfying(
                        PagamentoPixException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ApiErrorCode.PIX_PAGAMENTO_CONFIRMADO));

        assertThat(pagamento.getStatusInterno()).isEqualTo(StatusInternoPagamento.APROVADO);
        assertThat(pagamento.getCreditadoEm()).isNotNull();
        verify(gateway, never()).cancelarCobranca(any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void cobrancaJaRemovidaSincronizaSemRepetirRemocao() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid()))
                .thenReturn(cobranca(pagamento, "REMOVIDA_PELO_PSP", BigDecimal.ZERO));
        sincronizarComo(pagamento, StatusInternoPagamento.CANCELADO);

        var resultado = service.cancelar(
                pagamento.getId(),
                "cancelamento-removido",
                authentication,
                "req-cancelamento-removido");

        assertThat(resultado.status()).isEqualTo("CANCELADO");
        verify(gateway, never()).cancelarCobranca(any());
        verify(auditoriaRepository).save(any());
    }

    @Test
    void cobrancaExpiradaSincronizaSemRemocaoRemota() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid()))
                .thenReturn(cobranca(pagamento, "EXPIRADA", BigDecimal.ZERO));
        sincronizarComo(pagamento, StatusInternoPagamento.EXPIRADO);

        var resultado = service.cancelar(
                pagamento.getId(),
                "cancelamento-expirado",
                authentication,
                "req-cancelamento-expirado");

        assertThat(resultado.status()).isEqualTo("EXPIRADO");
        assertThat(resultado.cancelavel()).isFalse();
        verify(gateway, never()).cancelarCobranca(any());
    }

    @Test
    void indisponibilidadePreservaCobrancaPendenteESanitizaErro() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid()))
                .thenThrow(new EfiPixGatewayException(
                        "detalhe externo oauth tls certificado",
                        false,
                        503));

        assertThatThrownBy(() -> service.cancelar(
                pagamento.getId(),
                "cancelamento-indisponivel",
                authentication,
                "req-cancelamento-indisponivel"))
                .isInstanceOfSatisfying(
                        PagamentoPixException.class,
                        exception -> {
                            assertThat(exception.code())
                                    .isEqualTo(ApiErrorCode.PIX_CANCELAMENTO_INDISPONIVEL);
                            assertThat(exception.getMessage())
                                    .doesNotContainIgnoringCase("oauth")
                                    .doesNotContainIgnoringCase("tls")
                                    .doesNotContainIgnoringCase("certificado");
                        });

        assertThat(pagamento.getStatusInterno())
                .isEqualTo(StatusInternoPagamento.AGUARDANDO_PAGAMENTO);
        verify(gateway, never()).cancelarCobranca(any());
        verify(conciliacaoService, never()).aplicarCobrancaConsultada(
                any(), any(), any(), any(), any());
        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void repeticaoDepoisDoCancelamentoNaoRepeteEfeito() {
        PagamentoEntity pagamento = pagamentoPendente();
        prepararOwnership(pagamento);
        when(gateway.consultarCobrancaSemQrCode(pagamento.getTxid()))
                .thenReturn(cobranca(pagamento, "ATIVA", BigDecimal.ZERO));
        when(gateway.cancelarCobranca(pagamento.getTxid()))
                .thenReturn(cobranca(
                        pagamento,
                        "REMOVIDA_PELO_USUARIO_RECEBEDOR",
                        BigDecimal.ZERO));
        sincronizarComo(pagamento, StatusInternoPagamento.CANCELADO);

        var primeiro = service.cancelar(
                pagamento.getId(),
                "cancelamento-repetido",
                authentication,
                "req-cancelamento-repetido");
        var segundo = service.cancelar(
                pagamento.getId(),
                "cancelamento-repetido",
                authentication,
                "req-cancelamento-repetido");

        assertThat(primeiro.status()).isEqualTo("CANCELADO");
        assertThat(segundo.status()).isEqualTo("CANCELADO");
        assertThat(segundo.idempotente()).isTrue();
        verify(gateway, times(1)).cancelarCobranca(pagamento.getTxid());
        verify(auditoriaRepository, times(1)).save(any());
    }

    private void prepararOwnership(PagamentoEntity pagamento) {
        when(pagamentoRepository.findByIdAndUsuarioIdForUpdate(pagamento.getId(), usuarioId))
                .thenReturn(Optional.of(pagamento));
    }

    private void sincronizarComo(
            PagamentoEntity pagamento,
            StatusInternoPagamento statusInterno) {
        when(conciliacaoService.aplicarCobrancaConsultada(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    EfiPixGateway.CobrancaPix cobranca = invocation.getArgument(0);
                    pagamento.atualizarStatusProvedor(
                            statusInterno,
                            cobranca.status(),
                            OffsetDateTime.now(ZoneOffset.UTC));
                    return new EfiPagamentoConciliacaoService.ConciliacaoResultado(
                            pagamento,
                            cobranca,
                            false,
                            null);
                });
    }

    private PagamentoEntity pagamentoPendente() {
        UUID planoId = UUID.randomUUID();
        PagamentoEntity pagamento = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
                new BigDecimal("9.90"),
                100,
                "checkout-cancelamento",
                OffsetDateTime.now(ZoneOffset.UTC));
        pagamento.aguardarPagamento(
                "localizacao-teste",
                "ATIVA",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                OffsetDateTime.now(ZoneOffset.UTC));
        return pagamento;
    }

    private EfiPixGateway.CobrancaPix cobranca(
            PagamentoEntity pagamento,
            String status,
            BigDecimal valorRecebido) {
        return new EfiPixGateway.CobrancaPix(
                AmbientePagamento.SANDBOX,
                pagamento.getTxid(),
                status,
                "123",
                pagamento.getValor(),
                valorRecebido,
                pagamento.getExpiracaoEm(),
                null,
                null);
    }
}