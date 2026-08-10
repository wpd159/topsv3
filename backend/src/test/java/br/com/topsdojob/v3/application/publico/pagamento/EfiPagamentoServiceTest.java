package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class EfiPagamentoServiceTest {

    private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PlanoCreditoRepository planoRepository = mock(PlanoCreditoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
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
            gateway,
            conciliacaoService,
            rateLimiter,
            transactionManager);

    @BeforeEach
    void setUp() {
        when(gateway.ambiente()).thenReturn(AmbientePagamento.SANDBOX);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(pagamentoRepository.findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void criaCobrancaComTxidDeterministicoEContratoDoPacote() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(plano.getId()).thenReturn(planoId);
        when(plano.getAtivo()).thenReturn(true);
        when(plano.getValor()).thenReturn(new BigDecimal("5.00"));
        when(plano.getQuantidadeCreditos()).thenReturn(50);
        when(plano.getMoeda()).thenReturn("BRL");
        when(plano.getNome()).thenReturn("50 creditos");
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PagamentoEntity salvo = invocation.getArgument(0);
            when(pagamentoRepository.findByTxidForUpdate(salvo.getTxid())).thenReturn(Optional.of(salvo));
            return salvo;
        });
        when(gateway.criarCobranca(any(), any(), any())).thenAnswer(invocation -> cobranca(
                invocation.getArgument(0),
                new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-teste-0001",
                authentication);

        ArgumentCaptor<PagamentoEntity> captor = ArgumentCaptor.forClass(PagamentoEntity.class);
        verify(pagamentoRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTxid()).matches("[a-f0-9]{32}");
        assertThat(captor.getValue().getAmbiente()).isEqualTo(AmbientePagamento.SANDBOX);
        assertThat(resultado.identificacaoSanitizada()).endsWith(captor.getValue().getTxid().substring(24));
        assertThat(resultado.valor()).isEqualByComparingTo("5.00");
        assertThat(resultado.quantidadeCreditos()).isEqualTo(50);
        assertThat(resultado.idempotente()).isFalse();
    }

    @Test
    void repeticaoDaChaveReutilizaPagamentoSemNovaCobranca() {
        UUID usuarioId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        UUID planoId = UUID.randomUUID();
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                "a".repeat(32),
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":checkout-teste-0002",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existente));
        when(pagamentoRepository.findByTxidForUpdate(existente.getTxid())).thenReturn(Optional.of(existente));
        when(gateway.consultarCobranca(existente.getTxid())).thenReturn(cobranca(existente.getTxid(), new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-teste-0002",
                authentication);

        assertThat(resultado.idempotente()).isTrue();
        verify(gateway).consultarCobranca(existente.getTxid());
    }

    @Test
    void reconsultaChaveDepoisDoLockEReutilizaCobrancaConcorrente() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        PagamentoEntity existente = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                "c".repeat(32),
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":checkout-concorrente",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByIdempotencyKey(any()))
                .thenReturn(Optional.empty(), Optional.of(existente));
        when(pagamentoRepository.findByTxidForUpdate(existente.getTxid())).thenReturn(Optional.of(existente));
        when(gateway.consultarCobranca(existente.getTxid()))
                .thenReturn(cobranca(existente.getTxid(), new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-concorrente",
                authentication);

        assertThat(resultado.idempotente()).isTrue();
        verify(pagamentoRepository, never()).saveAndFlush(any());
        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void chaveReutilizadaComOutroPacoteRetornaConflito() {
        UUID usuarioId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                UUID.randomUUID(),
                AmbientePagamento.SANDBOX,
                "d".repeat(32),
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":checkout-divergente",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(UUID.randomUUID()),
                "checkout-divergente",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void novaChaveReutilizaCobrancaPendenteDoMesmoPlano() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        PagamentoEntity existente = pagamentoPendente(usuarioId, planoId, "e".repeat(32));
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(pagamentoRepository.findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                eq(usuarioId), any(), any(), any())).thenReturn(List.of(existente));
        when(plano.getNome()).thenReturn("Pacote pendente");
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(gateway.consultarCobranca(existente.getTxid()))
                .thenReturn(cobranca(existente.getTxid(), existente.getValor()));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "nova-chave-mesmo-plano",
                authentication);

        assertThat(resultado.pagamentoId()).isEqualTo(existente.getId());
        assertThat(resultado.idempotente()).isTrue();
        verify(pagamentoRepository, never()).saveAndFlush(any());
        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void novaChaveNaoCriaOutroPlanoEnquantoExisteCobrancaPendente() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoPendenteId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PagamentoEntity existente = pagamentoPendente(usuarioId, planoPendenteId, "f".repeat(32));
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(pagamentoRepository.findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                eq(usuarioId), any(), any(), any())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(UUID.randomUUID()),
                "nova-chave-outro-plano",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        verify(pagamentoRepository, never()).saveAndFlush(any());
        verify(gateway, never()).consultarCobranca(any());
        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void conciliacaoIndisponivelRetornaBadGatewaySanitizado() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PagamentoEntity pagamento = pagamentoPendente(usuarioId, planoId, "1".repeat(32));
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(pagamentoRepository.findByIdAndUsuarioId(pagamento.getId(), usuarioId))
                .thenReturn(Optional.of(pagamento));
        when(conciliacaoService.conciliar(any(), any(), any(), any(), any()))
                .thenThrow(new EfiPixGatewayException("indisponibilidade sintetica", false, 503));

        assertThatThrownBy(() -> service.conciliar(
                pagamento.getId(),
                authentication,
                "req-pix-conciliacao-indisponivel"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("502 BAD_GATEWAY");

        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void conciliacao404RecuperaMesmaIntencaoSemNovoPagamento() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        PagamentoEntity pagamento = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                "2".repeat(32),
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":recuperacao",
                OffsetDateTime.now(ZoneOffset.UTC));
        pagamento.atualizarStatusProvedor(
                StatusInternoPagamento.ERRO,
                EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO,
                OffsetDateTime.now(ZoneOffset.UTC));
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(pagamentoRepository.findByIdAndUsuarioId(pagamento.getId(), usuarioId))
                .thenReturn(Optional.of(pagamento));
        when(pagamentoRepository.findByTxidForUpdate(pagamento.getTxid()))
                .thenReturn(Optional.of(pagamento));
        when(plano.getNome()).thenReturn("Pacote recuperado");
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(conciliacaoService.conciliar(any(), any(), any(), any(), any()))
                .thenThrow(new EfiPixGatewayException("ausente sintetico", false, 404));
        when(gateway.criarCobranca(eq(pagamento.getTxid()), any(), any()))
                .thenReturn(cobranca(pagamento.getTxid(), pagamento.getValor()));

        var resultado = service.conciliar(
                pagamento.getId(),
                authentication,
                "req-pix-conciliacao-recuperacao");

        assertThat(resultado.pagamentoId()).isEqualTo(pagamento.getId());
        assertThat(resultado.status()).isEqualTo("PENDENTE");
        assertThat(resultado.idempotente()).isTrue();
        verify(pagamentoRepository, never()).saveAndFlush(any());
        verify(gateway).criarCobranca(eq(pagamento.getTxid()), any(), any());
    }
    @Test
    void historicoRetornaPlanoSnapshotFinanceiroEIdentificacaoSanitizada() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity pagamento = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                "1234567890abcdef1234567890abcdef",
                new BigDecimal("8.75"),
                90,
                "checkout-historico",
                OffsetDateTime.parse("2026-07-28T18:00:00Z"));
        pagamento.atualizarStatusProvedor(
                StatusInternoPagamento.ERRO,
                EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO,
                OffsetDateTime.parse("2026-07-28T18:00:01Z"));
        when(pagamentoRepository.findByUsuarioIdAndProvedorAndMetodoOrderByCriadoEmDesc(
                eq(usuarioId),
                any(),
                any(),
                any())).thenReturn(List.of(pagamento));
        when(plano.getId()).thenReturn(planoId);
        when(plano.getNome()).thenReturn("Pacote QA");
        when(planoRepository.findAllById(any())).thenReturn(List.of(plano));

        var historico = service.historico(authentication);

        assertThat(historico).singleElement().satisfies(item -> {
            assertThat(item.planoNome()).isEqualTo("Pacote QA");
            assertThat(item.valor()).isEqualByComparingTo("8.75");
            assertThat(item.quantidadeCreditos()).isEqualTo(90);
            assertThat(item.status()).isEqualTo("PENDENTE");
            assertThat(item.identificacaoSanitizada()).isEqualTo("PIX **** 90abcdef");
            assertThat(item.identificacaoSanitizada()).doesNotContain(pagamento.getTxid());
        });
    }

    @Test
    void pacoteInativoNaoCriaCobranca() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(plano.getAtivo()).thenReturn(false);
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-inativo",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
        verify(gateway, never()).criarCobranca(any(), any(), any());
    }

    @Test
    void pacoteComValorInvalidoNaoCriaCobranca() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(plano.getAtivo()).thenReturn(true);
        when(plano.getValor()).thenReturn(BigDecimal.ZERO);
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-valor-invalido",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");

        verify(gateway, never()).criarCobranca(any(), any(), any());
        verify(pagamentoRepository, never()).saveAndFlush(any());
    }


    @Test
    void usuarioNaoConsultaPagamentoAlheio() {
        UUID usuarioId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(pagamentoRepository.findByIdAndUsuarioId(pagamentoId, usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(pagamentoId, authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
        verify(gateway, never()).consultarCobranca(any());
    }

    @Test
    void falhaDoProvedorNaoRetornaSucessoFalso() {
        UUID usuarioId = UUID.randomUUID();
        UUID planoId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        PlanoCreditoEntity plano = mock(PlanoCreditoEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        when(usuarioRepository.findByIdForUpdate(usuarioId)).thenReturn(Optional.of(usuario));
        when(plano.getId()).thenReturn(planoId);
        when(plano.getAtivo()).thenReturn(true);
        when(plano.getValor()).thenReturn(new BigDecimal("5.00"));
        when(plano.getQuantidadeCreditos()).thenReturn(50);
        when(plano.getMoeda()).thenReturn("BRL");
        when(planoRepository.findById(planoId)).thenReturn(Optional.of(plano));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(pagamentoRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.criarCobranca(any(), any(), any()))
                .thenThrow(new EfiPixGatewayException("falha sintetica", false));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-falha-provedor",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("502 BAD_GATEWAY");
    }

    @Test
    void usuarioBloqueadoEhRecusadoAntesDoProviderEDaPersistencia() {
        Authentication authentication = mock(Authentication.class);
        when(usuarioService.usuarioAutenticado(authentication))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "sessao publica invalida"));

        assertThatThrownBy(() -> service.criar(
                new EfiPixCheckoutRequest(UUID.randomUUID()),
                "checkout-usuario-bloqueado",
                authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verify(gateway, never()).criarCobranca(any(), any(), any());
        verify(pagamentoRepository, never()).saveAndFlush(any());
    }

    private PagamentoEntity pagamentoPendente(UUID usuarioId, UUID planoId, String txid) {
        PagamentoEntity pagamento = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                planoId,
                AmbientePagamento.SANDBOX,
                txid,
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":pendente",
                OffsetDateTime.now(ZoneOffset.UTC));
        pagamento.aguardarPagamento(
                "localizacao-sintetica",
                "ATIVA",
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                OffsetDateTime.now(ZoneOffset.UTC));
        return pagamento;
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
