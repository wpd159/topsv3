package br.com.topsdojob.v3.application.publico.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatConversaDto(
        UUID id,
        String participanteUsername,
        String ultimaMensagem,
        OffsetDateTime ultimaMensagemEm,
        long naoLidas) {
}
