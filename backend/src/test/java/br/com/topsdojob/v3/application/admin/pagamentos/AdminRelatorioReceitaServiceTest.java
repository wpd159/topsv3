package br.com.topsdojob.v3.application.admin.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.persistence.repository.admin.AdminRelatorioReceitaJdbcRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminRelatorioReceitaServiceTest {

    private final AdminRelatorioReceitaJdbcRepository repository =
            mock(AdminRelatorioReceitaJdbcRepository.class);
    private final AdminRelatorioReceitaService service = new AdminRelatorioReceitaService(
            repository,
            new PagamentoSanitizer(),
            Clock.fixed(Instant.parse("2026-07-28T02:30:00Z"), ZoneOffset.UTC));

    @Test
    void calculaReceitaTicketMedioEPeriodoNoFusoCanonico() {
        when(repository.metricas(any())).thenReturn(new AdminRelatorioReceitaJdbcRepository.MetricasRow(
                new BigDecimal("150.00"), 2, 15, 3, 1, 4, 2));
        when(repository.evolucaoDiaria(any())).thenReturn(List.of(
                new AdminRelatorioReceitaJdbcRepository.PontoDiarioRow(
                        LocalDate.of(2026, 7, 27), new BigDecimal("150.00"), 2)));
        when(repository.distribuicaoPorProduto(any())).thenReturn(List.of(
                new AdminRelatorioReceitaJdbcRepository.ProdutoRow(
                        "PACOTE_10", "Pacote 10", new BigDecimal("150.00"), 2, 15)));
        when(repository.conciliacao(any())).thenReturn(
                new AdminRelatorioReceitaJdbcRepository.ConciliacaoRow(1, 0, 0));

        var resumo = service.resumo("HOJE", null, null, "TODOS", "TODOS", null, null);

        assertThat(resumo.receitaConfirmada()).isEqualByComparingTo("150.00");
        assertThat(resumo.ticketMedio()).isEqualByComparingTo("75.00");
        assertThat(resumo.creditosVendidos()).isEqualTo(15);
        assertThat(resumo.dataInicio()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(resumo.dataFim()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(resumo.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(resumo.alertasConciliacao()).extracting("codigo")
                .containsExactly("PAGAMENTO_SEM_CONCILIACAO");
        assertThat(resumo.somenteLeitura()).isTrue();
    }

    @Test
    void ticketMedioZeroNaoFabricaReceita() {
        when(repository.metricas(any())).thenReturn(new AdminRelatorioReceitaJdbcRepository.MetricasRow(
                BigDecimal.ZERO, 0, 0, 0, 0, 0, 0));
        when(repository.evolucaoDiaria(any())).thenReturn(List.of());
        when(repository.distribuicaoPorProduto(any())).thenReturn(List.of());
        when(repository.conciliacao(any())).thenReturn(
                new AdminRelatorioReceitaJdbcRepository.ConciliacaoRow(0, 0, 0));

        var resumo = service.resumo("7_DIAS", null, null, "TODOS", "TODOS", null, null);

        assertThat(resumo.ticketMedio()).isEqualByComparingTo("0.00");
        assertThat(resumo.receitaConfirmada()).isEqualByComparingTo("0");
        assertThat(resumo.alertasConciliacao()).isEmpty();
    }

    @Test
    void transacaoExibeContatoAdministrativoEProtegeIdentificadorExterno() {
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID usuarioId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        var row = new AdminRelatorioReceitaJdbcRepository.TransacaoRow(
                id,
                usuarioId,
                OffsetDateTime.parse("2026-07-27T14:00:00Z"),
                "Conta QA",
                "qa.financeiro@example.invalid",
                "+5562999999999",
                "PACOTE_10",
                "Pacote 10",
                new BigDecimal("75.00"),
                "BRL",
                10,
                "APROVADO",
                "PIX",
                "txid-financeiro-secreto-1234");
        when(repository.transacoes(any(), anyInt(), anyInt(), anyString())).thenReturn(
                new AdminRelatorioReceitaJdbcRepository.PaginaRow(
                        List.of(row), 1, new BigDecimal("75.00"), 1));

        var pagina = service.transacoes(
                "30_DIAS", null, null, "TODOS", "TODOS", null, null,
                "MAIS_RECENTES", 0, 20);

        assertThat(pagina.itens()).singleElement().satisfies(item -> {
            assertThat(item.usuarioId()).isEqualTo(usuarioId);
            assertThat(item.usuarioEmail()).isEqualTo("qa.financeiro@example.invalid");
            assertThat(item.usuarioWhatsapp()).isEqualTo("+5562999999999");
            assertThat(item.identificadorExternoMascarado()).isEqualTo("***1234");
            assertThat(item.status()).isEqualTo("CONFIRMADO");
            assertThat(item.receitaConfirmada()).isTrue();
        });
    }

    @Test
    void rejeitaPeriodoPersonalizadoInvalido() {
        assertThatThrownBy(() -> service.resumo(
                "PERSONALIZADO",
                LocalDate.of(2026, 7, 28),
                LocalDate.of(2026, 7, 27),
                "TODOS",
                "TODOS",
                null,
                null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fim");
    }
}
