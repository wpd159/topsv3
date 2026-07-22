package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminModeracaoHistoricoItemDto(
        UUID id,
        String alvoTipo,
        UUID alvoId,
        String acao,
        String decisao,
        String motivo,
        String status,
        UUID atorId,
        String requestId,
        String resultado,
        OffsetDateTime criadoEm) {
}
