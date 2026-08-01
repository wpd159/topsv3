package br.com.topsdojob.v3.application.publico.chat.dto;

import java.util.UUID;

public record ChatNovaConversaRequestDto(UUID anuncioId, String username) {
}
