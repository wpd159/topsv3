package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdminDesempenhoAnuncianteDto(
        UUID usuarioId,
        long anunciosTotal,
        long visualizacoesTotal,
        long cliquesWhatsappTotal,
        BigDecimal taxaCliqueView,
        List<AdminDesempenhoAnuncioDto> anuncios,
        String avisoResultado,
        boolean endpointAnuncianteRealDisponivel,
        boolean dadosSensiveisOcultos,
        boolean somenteLeitura) {
}
