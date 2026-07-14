package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCreditoOperacaoDto(
        UUID movimentoId,
        UUID usuarioId,
        String natureza,
        int quantidade,
        int saldoAnterior,
        int saldoPosterior,
        String motivo,
        String requestId,
        OffsetDateTime criadoEm,
        boolean idempotente) {
}
