package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPremiumFilaItemDto(
        UUID ativacaoId,
        String codigo,
        String nome,
        String status,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm) {
}
