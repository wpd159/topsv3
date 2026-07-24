package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminDecidirFotosLoteResponseDto(
        UUID anuncioId,
        List<AdminResultadoFotoLoteItemDto> resultados,
        int aprovadas,
        int excluidas,
        int jaProcessadas,
        int falhas,
        boolean concluido,
        String requestId,
        OffsetDateTime processadoEm) {
}
