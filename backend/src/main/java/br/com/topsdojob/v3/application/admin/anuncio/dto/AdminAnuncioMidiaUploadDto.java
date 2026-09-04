package br.com.topsdojob.v3.application.admin.anuncio.dto;

import java.util.UUID;

public record AdminAnuncioMidiaUploadDto(
        UUID midiaId,
        UUID anuncioId,
        String tipo,
        String finalidade,
        Integer ordem,
        String status,
        String statusArquivo,
        boolean idempotente,
        String requestId) {
}
