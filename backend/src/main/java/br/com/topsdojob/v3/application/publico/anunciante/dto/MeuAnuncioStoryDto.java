package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MeuAnuncioStoryDto(
    UUID storyId,
    UUID anuncioId,
    String modoConteudo,
    String tipoMidia,
    String status,
    OffsetDateTime inicioEm,
    OffsetDateTime fimEm,
    String estadoMidia) {
}
