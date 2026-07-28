package br.com.topsdojob.v3.application.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.service.MidiaPublicaUrlService;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository.AnuncioDesempenhoRow;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository.SerieDiariaRow;
import br.com.topsdojob.v3.persistence.repository.admin.AdminDashboardAnalyticsJdbcRepository.TopWhatsappRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminDashboardAnalyticsServiceTest {

    private AdminDashboardAnalyticsJdbcRepository repository;
    private AdminDashboardAnalyticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdminDashboardAnalyticsJdbcRepository.class);
        service = new AdminDashboardAnalyticsService(
                repository,
                mock(AnuncioMidiaRepository.class),
                mock(ArquivoMidiaRepository.class),
                mock(MidiaPublicaUrlService.class),
                Clock.fixed(Instant.parse("2026-07-28T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aceitaSomentePeriodosSeteQuinzeETrintaEPreservaDiasSemEvento() {
        when(repository.serieDiaria(any(), any())).thenReturn(List.of(
                new SerieDiariaRow(LocalDate.of(2026, 7, 27), 100, 10)));

        assertThat(service.desempenhoDiario(7).serieDiaria()).hasSize(7);
        assertThat(service.desempenhoDiario(15).serieDiaria()).hasSize(15);
        assertThat(service.desempenhoDiario(30).serieDiaria()).hasSize(30);
        assertThat(service.desempenhoDiario(7).hoje().visualizacoes()).isZero();
        assertThat(service.desempenhoDiario(7).ontem().conversaoPct()).isEqualByComparingTo("10.00");
        assertThat(service.desempenhoDiario(7).variacaoVisualizacoesPct()).isEqualByComparingTo("-100.00");
        assertThat(service.desempenhoDiario(7).fusoHorario()).isEqualTo("America/Sao_Paulo");

        assertThatThrownBy(() -> service.desempenhoDiario(14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7, 15 ou 30");
    }

    @Test
    void evitaDivisaoPorZeroEUsaVariacaoCanonicaQuandoOntemFoiZero() {
        when(repository.serieDiaria(any(), any())).thenReturn(List.of(
                new SerieDiariaRow(LocalDate.of(2026, 7, 27), 0, 0),
                new SerieDiariaRow(LocalDate.of(2026, 7, 28), 20, 4)));

        var result = service.desempenhoDiario(7);

        assertThat(result.ontem().conversaoPct()).isEqualByComparingTo("0.00");
        assertThat(result.hoje().conversaoPct()).isEqualByComparingTo("20.00");
        assertThat(result.variacaoVisualizacoesPct()).isEqualByComparingTo("100.00");
        assertThat(result.variacaoCliquesPct()).isEqualByComparingTo("100.00");
    }

    @Test
    void limitaRankingDoDiaEInformaMostrarMaisSemConsultaPorAnuncio() {
        List<TopWhatsappRow> rows = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> new TopWhatsappRow(
                        uuid(index),
                        "Anuncio " + index,
                        "anuncio-" + index,
                        "Goiania",
                        "GO",
                        100 - index,
                        null,
                        true))
                .toList();
        when(repository.topWhatsappHoje(LocalDate.of(2026, 7, 28), 13)).thenReturn(rows);

        var result = service.topWhatsappHoje(12);

        assertThat(result.itens()).hasSize(12);
        assertThat(result.temMais()).isTrue();
        assertThat(result.itens().get(0).cliquesWhatsappHoje()).isEqualTo(99);
        verify(repository).topWhatsappHoje(LocalDate.of(2026, 7, 28), 13);
    }

    @Test
    void calculaRankingsCidadesClassificacoesAlertasEOportunidadesSemMocks() {
        when(repository.desempenhoPublicados(any())).thenReturn(List.of(
                row(1, "A", "Goiania", "goiania", "GO", "LIVRE", 100L, 20, 0),
                row(2, "B", "Goiania", "goiania", "GO", "RESTRITA_18", 1_000L, 0, 0),
                row(3, "C", "Goiania", "goiania", "GO", "LIVRE", 50L, 0, 1),
                row(4, "D", "Anapolis", "anapolis", "GO", "LIVRE", 200L, 10, 0)));
        when(repository.countBeneficiosVencendo(any(), any())).thenReturn(2L);

        var result = service.analises(true);

        assertThat(result.topConversao()).extracting(item -> item.titulo())
                .containsExactly("A", "D", "B", "C");
        assertThat(result.piorConversaoComTrafego()).extracting(item -> item.titulo())
                .containsExactly("B", "D", "A");
        assertThat(result.minimoVisualizacoesPiorConversao()).isEqualTo(100);
        assertThat(result.desempenhoPorCidade()).hasSize(2);
        assertThat(result.desempenhoPorClassificacao()).extracting(item -> item.classificacao())
                .containsExactly("LIVRE", "RESTRITA_18");
        assertThat(result.alertasPrioritarios()).extracting(item -> item.codigo())
                .contains("ANUNCIOS_COM_VIEWS_SEM_CLIQUE", "ALTO_TRAFEGO_SEM_CLIQUE");
        assertThat(result.oportunidadesComerciais()).extracting(item -> item.codigo())
                .contains("SEM_PREMIUM", "PREMIUM_VENCENDO");
    }

    @Test
    void moderadorNaoRecebeIndicadoresComerciaisRestritos() {
        when(repository.desempenhoPublicados(any())).thenReturn(List.of());

        var result = service.analises(false);

        assertThat(result.oportunidadesComerciais()).isEmpty();
        verify(repository, never()).countBeneficiosVencendo(any(), any());
    }

    private static AnuncioDesempenhoRow row(
            int id,
            String titulo,
            String cidade,
            String cidadeSlug,
            String uf,
            String classificacao,
            Long visualizacoes,
            long cliques,
            long premium) {
        return new AnuncioDesempenhoRow(
                uuid(id),
                titulo,
                titulo.toLowerCase(),
                cidade,
                cidadeSlug,
                uf,
                classificacao,
                visualizacoes,
                cliques,
                premium);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
