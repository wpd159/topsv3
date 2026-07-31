package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;

public record MeuAnuncioBeneficioDto(
        String codigo,
        String nome,
        String status,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm,
        Integer duracaoDias,
        Integer diasRestantes,
        String motivoEspera,
        String origem) {
}
