package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPagamentoConsistenciaItemDto(
        UUID usuarioId,
        UUID pagamentoId,
        UUID movimentoCreditoId,
        String codigo,
        String severidade,
        String mensagem,
        OffsetDateTime detectadoEm,
        boolean somenteLeitura) {
}
