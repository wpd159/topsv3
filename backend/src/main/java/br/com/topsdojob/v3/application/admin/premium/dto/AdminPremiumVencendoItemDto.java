package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPremiumVencendoItemDto(
        UUID anuncioId,
        String slug,
        UUID ativacaoId,
        String beneficioCodigo,
        String statusCalculado,
        OffsetDateTime fimEm,
        long diasRestantes,
        boolean grupoVinculado,
        boolean somenteLeitura) {
}
