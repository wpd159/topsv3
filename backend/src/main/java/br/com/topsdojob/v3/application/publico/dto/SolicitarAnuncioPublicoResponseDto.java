package br.com.topsdojob.v3.application.publico.dto;

import java.util.UUID;

public record SolicitarAnuncioPublicoResponseDto(
        boolean criado,
        UUID anuncioId,
        UUID revisaoId,
        String slugLocal,
        String statusAnuncio,
        String statusModeracao,
        String classificacaoConteudo,
        boolean publicado,
        boolean revisaoCriada,
        boolean publicacaoAutomaticaExecutada,
        boolean uploadRealExecutado,
        boolean pagamentoCriado,
        boolean creditoCriado,
        boolean premiumObrigatorio,
        boolean emailRealEnviado,
        boolean whatsappRealEnviado,
        String mensagem) {
}
