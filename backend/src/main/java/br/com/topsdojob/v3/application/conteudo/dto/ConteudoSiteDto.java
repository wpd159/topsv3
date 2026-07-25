package br.com.topsdojob.v3.application.conteudo.dto;

import java.time.OffsetDateTime;

public record ConteudoSiteDto(
    String contentKey,
    String titulo,
    String corpo,
    Integer contentVersion,
    String contentHash,
    OffsetDateTime updatedAt) {
}
