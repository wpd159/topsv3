package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminDesempenhoResumoDto(
        long anunciosComMetricas,
        long visualizacoesTotal,
        long cliquesWhatsappTotal,
        BigDecimal taxaCliqueView,
        long visualizacoesOrganicas,
        long visualizacoesComPremium,
        String mensagemSegura,
        OffsetDateTime calculadoEm,
        boolean trackingExternoExecutado,
        boolean gratuitoLimitado,
        boolean dadosSensiveisOcultos,
        boolean somenteLeitura) {
}
