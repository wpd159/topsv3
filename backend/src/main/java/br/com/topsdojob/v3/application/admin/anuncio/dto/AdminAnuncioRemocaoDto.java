package br.com.topsdojob.v3.application.admin.anuncio.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAnuncioRemocaoDto(
    UUID anuncioId,
    String statusAnuncio,
    String statusModeracao,
    String acao,
    OffsetDateTime removidoEm,
    OffsetDateTime executadoEm) {
}
