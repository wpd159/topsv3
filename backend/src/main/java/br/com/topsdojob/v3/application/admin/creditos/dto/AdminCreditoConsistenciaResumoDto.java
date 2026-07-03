package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminCreditoConsistenciaResumoDto(
        List<AdminCreditoConsistenciaItemDto> itens,
        int total,
        OffsetDateTime calculadoEm,
        boolean somenteLeitura) {
}
