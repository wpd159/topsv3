package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.time.OffsetDateTime;

public record MeuAnuncioReprovacaoDto(
        String motivo,
        OffsetDateTime decididoEm) {
}
