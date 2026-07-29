package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.credito.CreditoLancamentoResultado;
import br.com.topsdojob.v3.application.credito.CreditoLedgerOperacaoService;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.persistence.entity.credito.MovimentoCreditoEntity;
import br.com.topsdojob.v3.persistence.entity.financeiro.PagamentoEntity;
import br.com.topsdojob.v3.persistence.repository.AuditoriaEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoConciliacaoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoEventoRepository;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.DirecaoMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemConciliacaoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.OrigemMovimentoCredito;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoMovimentoCredito;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EfiPagamentoConciliacaoServiceTest {

    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final PagamentoEventoRepository eventoRepository = mock(PagamentoEventoRepository.class);
    private final PagamentoConciliacaoRepository conciliacaoRepository = mock(PagamentoConciliacaoRepository.class);
    private final AuditoriaEventoRepository auditoriaRepository = mock(AuditoriaEventoRepository.class);
    private final CreditoLedgerOperacaoService ledgerService = mock(CreditoLedgerOperacaoService.class);
    private final EfiPixGateway gateway = mock(EfiPixGateway.class);
    private final EfiPagamentoConciliacaoService service = new EfiPagamentoConciliacaoService(
            pagamentoRepository,
            eventoRepository,
            conciliacaoRepository,
            auditoriaRepository,
            ledgerService,
            gateway);
    private PagamentoEntity pagamento;

    @BeforeEach
    void setUp() {
        pagamento = PagamentoEntity.criarPixEfi(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "b".repeat(32),
                new BigDecimal("5.00"),
                50,
                "checkout-conciliacao",
                OffsetDateTime.now(ZoneOffset.UTC));
        pagamento.aguardarPagamento("123", "ATIVA", OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), OffsetDateTime.now(ZoneOffset.UTC));
        when(pagamentoRepository.findByTxidForUpdate(pagamento.getTxid())).thenReturn(Optional.of(pagamento));
        when(conciliacaoRepository.findByPagamentoId(pagamento.getId())).thenReturn(Optional.empty());
        when(eventoRepository.findByProvedorAndProvedorEventoId(any(), anyString())).thenReturn(Optional.empty());
        when(ledgerService.bloquearEConsultarSaldo(pagamento.getUsuarioId())).thenReturn(10);
        MovimentoCreditoEntity movimento = MovimentoCreditoEntity.registrar(
                UUID.randomUUID(),
                pagamento.getUsuarioId(),
                TipoMovimentoCredito.ENTRADA,
                DirecaoMovimentoCredito.CREDITO,
                50,
                10,
                60,
                OrigemMovimentoCredito.PAGAMENTO,
                "PAGAMENTO",
                pagamento.getId(),
                "efi-pagamento:" + pagamento.getId(),
                null,
                "Confirmacao Pix Efi conciliada",
                "req-teste",
                OffsetDateTime.now(ZoneOffset.UTC));
        when(ledgerService.registrar(any(), any(), any(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new CreditoLancamentoResultado(movimento, false));
    }

    @Test
    void webhookRepetidoGeraUmUnicoCredito() {
        when(gateway.consultarCobranca(pagamento.getTxid())).thenReturn(confirmada(new BigDecimal("5.00")));

        service.conciliar(
                pagamento.getTxid(),
                "E12345678901234567890",
                "hash-1",
                OrigemConciliacaoPagamento.WEBHOOK,
                "req-1");
        service.conciliar(
                pagamento.getTxid(),
                "E12345678901234567891",
                "hash-2",
                OrigemConciliacaoPagamento.WEBHOOK,
                "req-2");

        assertThat(pagamento.getCreditadoEm()).isNotNull();
        assertThat(pagamento.getStatusInterno()).isEqualTo(StatusInternoPagamento.APROVADO);
        verify(ledgerService, times(1)).registrar(any(), any(), any(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void valorDivergenteNaoGeraCredito() {
        when(gateway.consultarCobranca(pagamento.getTxid())).thenReturn(confirmada(new BigDecimal("4.99")));

        service.conciliar(
                pagamento.getTxid(),
                "E12345678901234567892",
                "hash-3",
                OrigemConciliacaoPagamento.WEBHOOK,
                "req-3");

        assertThat(pagamento.getCreditadoEm()).isNull();
        assertThat(pagamento.getStatusInterno()).isEqualTo(StatusInternoPagamento.ERRO);
        verify(ledgerService, never()).registrar(any(), any(), any(), anyInt(), anyInt(), any(), anyString(), any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void webhookConfirmadoCreditaSemConsultaExternaAoProvedor() {
        OffsetDateTime recebidoEm = OffsetDateTime.parse("2026-07-28T20:00:00Z");

        service.conciliarWebhook(
                pagamento.getTxid(),
                "E12345678901234567893",
                "hash-4",
                new BigDecimal("5.00"),
                recebidoEm,
                "req-4");
        service.conciliarWebhook(
                pagamento.getTxid(),
                "E12345678901234567894",
                "hash-5",
                new BigDecimal("5.00"),
                recebidoEm,
                "req-5");

        assertThat(pagamento.getStatusInterno()).isEqualTo(StatusInternoPagamento.APROVADO);
        assertThat(pagamento.getAprovadoEm()).isEqualTo(recebidoEm);
        verify(gateway, never()).consultarCobranca(anyString());
        verify(ledgerService, times(1)).registrar(
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                anyString(),
                any(),
                anyString(),
                any(),
                anyString(),
                anyString());
    }

    @Test
    void webhookComValorDivergenteFalhaSemCredito() {
        assertThatThrownBy(() -> service.conciliarWebhook(
                pagamento.getTxid(),
                "E12345678901234567895",
                "hash-6",
                new BigDecimal("4.99"),
                OffsetDateTime.parse("2026-07-28T20:00:00Z"),
                "req-6"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        assertThat(pagamento.getCreditadoEm()).isNull();
        verify(ledgerService, never()).registrar(
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                anyString(),
                any(),
                anyString(),
                any(),
                anyString(),
                anyString());
    }

    @ParameterizedTest
    @EnumSource(
            value = StatusInternoPagamento.class,
            names = {"EXPIRADO", "CANCELADO"})
    void pagamentoTerminalNaoEhCreditadoPorCallbackTardio(StatusInternoPagamento status) {
        pagamento.atualizarStatusProvedor(status, status.name(), OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> service.conciliarWebhook(
                pagamento.getTxid(),
                "E12345678901234567896",
                "hash-7",
                new BigDecimal("5.00"),
                OffsetDateTime.parse("2026-07-28T20:00:00Z"),
                "req-7"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");

        assertThat(pagamento.getCreditadoEm()).isNull();
        verify(ledgerService, never()).registrar(
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                anyString(),
                any(),
                anyString(),
                any(),
                anyString(),
                anyString());
    }

    private EfiPixGateway.CobrancaPix confirmada(BigDecimal valorRecebido) {
        return new EfiPixGateway.CobrancaPix(
                pagamento.getTxid(),
                "CONCLUIDA",
                "123",
                new BigDecimal("5.00"),
                valorRecebido,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                "pix-sintetico",
                "data:image/svg+xml;base64,c2ludGV0aWNv");
    }
}
