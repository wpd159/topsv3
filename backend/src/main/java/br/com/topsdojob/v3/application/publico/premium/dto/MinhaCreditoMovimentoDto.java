package br.com.topsdojob.v3.application.publico.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MinhaCreditoMovimentoDto(
        UUID id,
        String natureza,
        int quantidade,
        int saldoAnterior,
        int saldoPosterior,
        String motivo,
        OffsetDateTime criadoEm) {
}
