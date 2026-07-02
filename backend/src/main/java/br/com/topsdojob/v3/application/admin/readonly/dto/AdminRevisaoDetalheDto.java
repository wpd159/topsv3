package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRevisaoDetalheDto(
        UUID id,
        UUID anuncioId,
        String slugAnuncio,
        String tipo,
        String status,
        boolean conteudoSolicitadoPresente,
        OffsetDateTime criadoEm,
        OffsetDateTime finalizadoEm,
        boolean somenteLeitura) {
}
