package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminDesempenhoComparativoPremiumDto(
        long visualizacoesOrganicas,
        long cliquesOrganicos,
        BigDecimal taxaCliqueViewOrganica,
        long visualizacoesComPremium,
        long cliquesComPremium,
        BigDecimal taxaCliqueViewPremium,
        List<String> beneficiosExposicaoAtivos,
        String mensagemSegura,
        boolean promessaResultadoGarantido,
        boolean gratuitoLimitado,
        boolean somenteLeitura) {
}
