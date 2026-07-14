package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPremiumAtivacaoDto(
        UUID id,
        UUID usuarioId,
        UUID anuncioId,
        String beneficioCodigo,
        String beneficioNome,
        String origem,
        String status,
        int custoCreditos,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm) {
}
