package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoryEncerramentoDto(
    UUID id,
    String status,
    OffsetDateTime encerradoEm,
    String origem,
    String motivo,
    boolean direitoPreservado,
    boolean repetido) {
}
