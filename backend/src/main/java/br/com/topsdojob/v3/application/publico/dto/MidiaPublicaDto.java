package br.com.topsdojob.v3.application.publico.dto;

import java.util.UUID;

public record MidiaPublicaDto(
        UUID id,
        String tipo,
        String finalidade,
        Integer ordem,
        String visibilidadeMidia,
        boolean autorizada,
        String urlPublica,
        String pendenciaMidia,
        Integer largura,
        Integer altura,
        String mimeType) {
}
