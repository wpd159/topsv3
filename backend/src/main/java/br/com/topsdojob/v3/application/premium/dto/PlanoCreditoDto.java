package br.com.topsdojob.v3.application.premium.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanoCreditoDto(
        UUID id,
        String codigo,
        String nome,
        String descricao,
        int quantidadeCreditos,
        BigDecimal valor,
        String moeda,
        boolean ativo,
        int ordemExibicao) {
}
