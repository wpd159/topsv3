package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPremiumConsistenciaItemDto(
        UUID anuncioId,
        String slug,
        UUID ativacaoId,
        String beneficioCodigo,
        String codigo,
        String severidade,
        String mensagem,
        String statusCalculado,
        OffsetDateTime fimEm,
        UUID grupoId,
        String grupoStatus,
        OffsetDateTime grupoFimEm,
        OffsetDateTime detectadoEm) {
}
