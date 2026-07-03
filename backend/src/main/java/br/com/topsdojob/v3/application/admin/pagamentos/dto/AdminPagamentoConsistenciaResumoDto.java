package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminPagamentoConsistenciaResumoDto(
        List<AdminPagamentoConsistenciaItemDto> itens,
        int total,
        OffsetDateTime calculadoEm,
        boolean somenteLeitura) {
}
