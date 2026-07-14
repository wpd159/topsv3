package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PlanoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.usuario.UsuarioEntity;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.repository.PlanoCreditoRepository;
import br.com.topsdojob.v3.persistence.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

class EfiPagamentoServiceTest {

    private final MeusAnunciosConsultaService usuarioService = mock(MeusAnunciosConsultaService.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PlanoCreditoRepository planoRepository = mock(PlanoCreditoRepository.class);
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final EfiPixGateway gateway = mock(EfiPixGateway.class);
    private final EfiPagamentoConciliacaoService conciliacaoService = mock(EfiPagamentoConciliacaoService.class);
    private final EfiPagamentoService service = new EfiPagamentoService(
            usuarioService,
            usuarioRepository,
            planoRepository,
            pagamentoRepository,
            gateway,
            conciliacaoService);

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
        when(pagamentoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.criarCobranca(any(), any(), any())).thenAnswer(invocation -> cobranca(
                invocation.getArgument(0),
                new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(planoId),
                "checkout-teste-0001",
                authentication);

        ArgumentCaptor<PagamentoEntity> captor = ArgumentCaptor.forClass(PagamentoEntity.class);
        verify(pagamentoRepository).save(captor.capture());
        assertThat(captor.getValue().getTxid()).matches("[a-f0-9]{32}");
        assertThat(resultado.txid()).isEqualTo(captor.getValue().getTxid());
        assertThat(resultado.valor()).isEqualByComparingTo("5.00");
        assertThat(resultado.quantidadeCreditos()).isEqualTo(50);
        assertThat(resultado.idempotente()).isFalse();
    }

    @Test
    void repeticaoDaChaveReutilizaPagamentoSemNovaCobranca() {
        UUID usuarioId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        UsuarioEntity usuario = mock(UsuarioEntity.class);
        when(usuario.getId()).thenReturn(usuarioId);
        when(usuarioService.usuarioAutenticado(authentication)).thenReturn(usuario);
        PagamentoEntity existente = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                usuarioId,
                UUID.randomUUID(),
                "a".repeat(32),
                new BigDecimal("5.00"),
                50,
                "efi-checkout:" + usuarioId + ":checkout-teste-0002",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existente));
        when(gateway.consultarCobranca(existente.getTxid())).thenReturn(cobranca(existente.getTxid(), new BigDecimal("5.00")));

        var resultado = service.criar(
                new EfiPixCheckoutRequest(UUID.randomUUID()),
                "checkout-teste-0002",
                authentication);

        assertThat(resultado.idempotente()).isTrue();
        verify(gateway).consultarCobranca(existente.getTxid());
    }

    private EfiPixGateway.CobrancaPix cobranca(String txid, BigDecimal valor) {
        return new EfiPixGateway.CobrancaPix(
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
