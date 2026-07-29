package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AdminPlanoCreditoDtos {

    private AdminPlanoCreditoDtos() {
    }

    public record CriarRequest(
            String codigo,
            String nome,
            String descricao,
            Integer quantidadeCreditos,
            BigDecimal valor,
            Boolean ativo,
            Integer ordemExibicao) {
    }

    public record AtualizarRequest(
            String nome,
            String descricao,
            Integer quantidadeCreditos,
            BigDecimal valor,
            Integer ordemExibicao,
            OffsetDateTime atualizadoEm) {
    }

    public record StatusRequest(OffsetDateTime atualizadoEm) {
    }

    public record Resumo(
            UUID id,
            String codigo,
            String nome,
            String descricao,
            int quantidadeCreditos,
            BigDecimal valor,
            String moeda,
            boolean ativo,
            int ordemExibicao,
            long comprasConfirmadas,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {
    }
}
