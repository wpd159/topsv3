package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.util.UUID;

public record AdminResultadoFotoLoteItemDto(
        UUID mediaId,
        String decisao,
        String classificacao,
        String resultado,
        String status,
        String motivo) {
}
