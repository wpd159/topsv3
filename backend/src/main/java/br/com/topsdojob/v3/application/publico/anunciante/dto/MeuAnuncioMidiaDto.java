package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.UUID;

public record MeuAnuncioMidiaDto(
        UUID id,
        String tipo,
        String finalidade,
        Integer ordem,
        String status,
        String visibilidadeMidia,
        String urlPublica,
        boolean restrita) {
}
