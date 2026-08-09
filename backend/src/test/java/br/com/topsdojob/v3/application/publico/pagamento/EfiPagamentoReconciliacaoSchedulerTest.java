package br.com.topsdojob.v3.application.publico.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGateway;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixGatewayException;
import br.com.topsdojob.v3.infrastructure.payment.efi.EfiPixProperties;
import br.com.topsdojob.v3.persistence.repository.PagamentoRepository;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusInternoPagamento;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class EfiPagamentoReconciliacaoSchedulerTest {

    private final EfiPixProperties properties = new EfiPixProperties();
    private final PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
    private final EfiPagamentoConciliacaoService conciliacaoService = mock(EfiPagamentoConciliacaoService.class);
    private final EfiPixGateway gateway = mock(EfiPixGateway.class);
    private final EfiPagamentoReconciliacaoScheduler scheduler = new EfiPagamentoReconciliacaoScheduler(
            properties,
            pagamentoRepository,
            conciliacaoService,
            gateway);

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setReconciliationEnabled(true);
        properties.setReconciliationBatchSize(2);
        properties.setReconciliationMaxPerCycle(3);
        properties.setReconciliationRetryBackoffSeconds(60);
        when(gateway.ambiente()).thenReturn(AmbientePagamento.SANDBOX);
    }

    @Test
    void schedulerDesabilitadoNaoExecuta() {
        properties.setReconciliationEnabled(false);

        scheduler.processar();

        verifyNoInteractions(pagamentoRepository, conciliacaoService);
        verify(gateway, never()).ambiente();
    }

    @Test
    void integracaoEfiDesabilitadaNaoExecuta() {
        properties.setEnabled(false);

        scheduler.processar();

        verifyNoInteractions(pagamentoRepository, conciliacaoService);
        verify(gateway, never()).ambiente();
    }

    @Test
    void cicloRespeitaLoteMaximoEEstadosRetentaveis() {
        String primeiro = "a".repeat(32);
        String segundo = "b".repeat(32);
        String terceiro = "c".repeat(32);
        when(pagamentoRepository.findTxidsConciliaveisEfi(
                any(), any(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(primeiro, segundo), List.of(terceiro));

        int processados = scheduler.processarCiclo();

        assertThat(processados).isEqualTo(3);
        verify(conciliacaoService, times(3)).conciliar(
                anyString(), any(), any(), any(), anyString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<StatusInternoPagamento>> statusCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(pagamentoRepository, atLeastOnce()).findTxidsConciliaveisEfi(
                any(),
                any(),
                eq(AmbientePagamento.SANDBOX),
                statusCaptor.capture(),
                eq(StatusInternoPagamento.ERRO),
                eq(EfiPagamentoConciliacaoService.STATUS_ERRO_TRANSITORIO),
                any(OffsetDateTime.class),
                pageableCaptor.capture());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(
                        StatusInternoPagamento.CRIADO,
                        StatusInternoPagamento.AGUARDANDO_PAGAMENTO)
                .doesNotContain(
                        StatusInternoPagamento.APROVADO,
                        StatusInternoPagamento.CANCELADO,
                        StatusInternoPagamento.EXPIRADO,
                        StatusInternoPagamento.ESTORNADO,
                        StatusInternoPagamento.LEGADO);
        assertThat(pageableCaptor.getAllValues().get(0).getPageSize()).isEqualTo(2);
        assertThat(pageableCaptor.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }

    @Test
    void indisponibilidadeTransitoriaNaoInterrompeNemCreditaPeloScheduler() {
        String primeiro = "d".repeat(32);
        String segundo = "e".repeat(32);
        when(pagamentoRepository.findTxidsConciliaveisEfi(
                any(), any(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(primeiro, segundo));
        when(conciliacaoService.conciliar(
                eq(primeiro), any(), any(), any(), anyString()))
                .thenThrow(new EfiPixGatewayException("indisponivel", false));

        assertThat(scheduler.processarCiclo()).isEqualTo(2);

        verify(conciliacaoService).conciliar(eq(segundo), any(), any(), any(), anyString());
    }

    @Test
    void segundoCicloSemElegiveisNaoRepetePagamento() {
        String txid = "f".repeat(32);
        when(pagamentoRepository.findTxidsConciliaveisEfi(
                any(), any(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(List.of(txid), List.of());

        assertThat(scheduler.processarCiclo()).isEqualTo(1);
        assertThat(scheduler.processarCiclo()).isZero();

        verify(conciliacaoService).conciliar(eq(txid), any(), any(), any(), anyString());
    }
}
