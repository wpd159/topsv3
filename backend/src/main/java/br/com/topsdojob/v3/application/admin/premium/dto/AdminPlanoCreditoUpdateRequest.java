package br.com.topsdojob.v3.application.admin.premium.dto;

import java.math.BigDecimal;

public record AdminPlanoCreditoUpdateRequest(
        String nome,
        String descricao,
        Integer quantidadeCreditos,
        BigDecimal valor,
        Boolean ativo,
        Integer ordemExibicao) {
}
