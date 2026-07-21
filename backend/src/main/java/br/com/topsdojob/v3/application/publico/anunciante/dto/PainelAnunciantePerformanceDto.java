package br.com.topsdojob.v3.application.publico.anunciante.dto;

import br.com.topsdojob.v3.application.metrica.VisualizacoesCanonicasDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PainelAnunciantePerformanceDto(
        VisualizacoesCanonicasDto visualizacoes,
        long totalCliquesWhatsapp,
        BigDecimal ctrGeral,
        long anunciosComBeneficioPremiumVigente,
        ComparativoCliquesDto comparativo,
        List<SerieCliquesDto> serieCliquesWhatsapp,
        List<AnuncioPerformanceDto> ranking) {

    public record ComparativoCliquesDto(
            long cliquesPeriodoAtual,
            long cliquesPeriodoAnterior,
            BigDecimal variacaoPercentual) {
    }

    public record SerieCliquesDto(
            LocalDate data,
            long cliques) {
    }

    public record AnuncioPerformanceDto(
            UUID anuncioId,
            String anuncioSlug,
            String anuncioTitulo,
            String localizacao,
            String fotoCapa,
            VisualizacoesCanonicasDto visualizacoes,
            long cliquesWhatsapp,
            BigDecimal ctr,
            long beneficiosPremiumVigentes) {
    }
}
