package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminPremiumVencendoResumoDto(
        List<AdminPremiumVencendoItemDto> itens,
        int total,
        int janelaDias,
        OffsetDateTime calculadoEm,
        boolean somenteLeitura) {
}
