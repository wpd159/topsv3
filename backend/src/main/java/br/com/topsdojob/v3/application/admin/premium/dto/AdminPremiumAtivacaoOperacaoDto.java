package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPremiumAtivacaoOperacaoDto(
        UUID id,
        UUID usuarioId,
        UUID anuncioId,
        UUID beneficioId,
        String status,
        int creditosEstornados,
        OffsetDateTime fimEm,
        boolean idempotente) {
}
