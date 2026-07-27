package br.com.topsdojob.v3.application.publico.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMensagemDto(
        UUID id,
        UUID conversaId,
        String remetenteUsername,
        String corpo,
        OffsetDateTime enviadoEm,
        OffsetDateTime lidoEm,
        boolean minha,
        boolean repetida) {
}
