package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCreditoConsistenciaItemDto(
        UUID usuarioId,
        UUID movimentoId,
        UUID pagamentoId,
        String codigo,
        String severidade,
        String mensagem,
        OffsetDateTime detectadoEm,
        boolean somenteLeitura) {
}
