package br.com.topsdojob.v3.application.blog.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlogCategoriaDto(
    UUID id,
    String nome,
    String slug,
    int ordem,
    boolean ativa,
    long postCountPublicados,
    long versao,
    OffsetDateTime updatedAt) {
}
