package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAuditoriaFinanceiraDto(
        UUID id,
        UUID administradorId,
        String acao,
        String recursoTipo,
        UUID recursoId,
        String requestId,
        OffsetDateTime criadoEm) {
}
