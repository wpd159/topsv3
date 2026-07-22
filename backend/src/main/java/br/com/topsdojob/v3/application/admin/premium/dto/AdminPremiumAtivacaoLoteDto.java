package br.com.topsdojob.v3.application.admin.premium.dto;

import java.util.List;

public record AdminPremiumAtivacaoLoteDto(
        List<AdminPremiumAtivacaoOperacaoDto> ativacoes,
        boolean idempotente) {
}
