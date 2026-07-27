package br.com.topsdojob.v3.application.publico.chat.dto;

import java.util.List;

public record ChatConversaDetalheDto(
        ChatConversaDto conversa,
        List<ChatMensagemDto> mensagens) {
}
