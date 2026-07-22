package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuAnuncioCicloVidaDto(
        UUID id,
        String slug,
        String status,
        String statusModeracao,
        OffsetDateTime atualizadoEm,
        MeuAnuncioAcoesDto acoesPermitidas) {
}
