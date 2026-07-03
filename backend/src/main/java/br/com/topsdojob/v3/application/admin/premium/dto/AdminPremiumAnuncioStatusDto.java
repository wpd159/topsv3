package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminPremiumAnuncioStatusDto(
        UUID anuncioId,
        String slug,
        String titulo,
        boolean premiumAtivo,
        boolean destaqueAtivo,
        boolean topoAtivo,
        boolean possuiStories,
        boolean possuiMidiaExtra,
        int beneficiosAtivos,
        int beneficiosExpirados,
        int beneficiosVencendo,
        int inconsistenciasTotal,
        List<String> codigosConsistencia,
        OffsetDateTime calculadoEm,
        boolean somenteLeitura,
        boolean compraOuAtivacaoRealDisponivel,
        boolean acoesFinanceirasDisponiveis,
        boolean gratuitoLimitadoPorContato) {
}
