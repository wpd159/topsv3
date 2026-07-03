package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;

public record AdminDesempenhoOrigemDto(
        String uf,
        String cidade,
        String bairro,
        long visualizacoes,
        long cliquesWhatsapp,
        BigDecimal taxaCliqueView,
        boolean origemAgregada,
        boolean somenteLeitura) {
}
