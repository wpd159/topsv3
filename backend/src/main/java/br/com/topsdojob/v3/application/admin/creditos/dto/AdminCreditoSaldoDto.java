package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminCreditoSaldoDto(
        UUID usuarioId,
        Integer saldoProjetado,
        Integer saldoCalculadoMovimentos,
        Integer saldoUltimoMovimento,
        int totalMovimentos,
        int totalEntradas,
        int totalSaidas,
        boolean consistente,
        List<String> codigosConsistencia,
        OffsetDateTime atualizadoEm,
        boolean somenteLeitura) {
}
