package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminPremiumConsistenciaResumoDto(
        List<AdminPremiumConsistenciaItemDto> itens,
        int total,
        OffsetDateTime calculadoEm,
        boolean somenteLeitura) {
}
