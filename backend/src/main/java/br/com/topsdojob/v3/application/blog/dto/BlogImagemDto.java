package br.com.topsdojob.v3.application.blog.dto;

import java.util.UUID;

public record BlogImagemDto(
    UUID id,
    String tipo,
    String previewUrl,
    String mimeType,
    String sha256,
    long tamanhoBytes,
    int largura,
    int altura) {
}
