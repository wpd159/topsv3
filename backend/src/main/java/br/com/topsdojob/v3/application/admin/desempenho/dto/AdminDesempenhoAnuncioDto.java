package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdminDesempenhoAnuncioDto(
        UUID anuncioId,
        UUID usuarioId,
        String slug,
        String titulo,
        long visualizacoesTotal,
        long cliquesWhatsappTotal,
        BigDecimal taxaCliqueView,
        List<AdminDesempenhoDiarioDto> diario,
        List<AdminDesempenhoOrigemDto> origens,
        AdminDesempenhoComparativoPremiumDto comparativoPremium,
        String avisoResultado,
        boolean dadosSensiveisOcultos,
        boolean trackingExternoExecutado,
        boolean somenteLeitura) {
}
