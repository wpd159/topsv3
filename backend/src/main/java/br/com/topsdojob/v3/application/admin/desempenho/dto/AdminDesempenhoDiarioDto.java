package br.com.topsdojob.v3.application.admin.desempenho.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminDesempenhoDiarioDto(
        LocalDate dataReferencia,
        long visualizacoes,
        long cliquesWhatsapp,
        BigDecimal taxaCliqueView,
        boolean premiumAtivo,
        boolean somenteLeitura) {
}
