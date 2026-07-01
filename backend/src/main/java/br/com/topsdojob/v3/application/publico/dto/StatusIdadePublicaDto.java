package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;

public record StatusIdadePublicaDto(
        boolean confirmada,
        OffsetDateTime expiraEm,
        String motivoPublico) {
}
