package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminCreditoMovimentoDto(
        UUID id,
        UUID usuarioId,
        String tipo,
        String direcao,
        Integer quantidade,
        Integer saldoAntes,
        Integer saldoDepois,
        String origem,
        String referenciaTipo,
        UUID referenciaId,
        String natureza,
        String motivo,
        UUID administradorId,
        String requestId,
        boolean chaveOperacionalPresente,
        OffsetDateTime criadoEm,
        boolean somenteLeitura) {
}
