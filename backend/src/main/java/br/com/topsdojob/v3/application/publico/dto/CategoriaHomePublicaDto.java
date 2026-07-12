package br.com.topsdojob.v3.application.publico.dto;

import java.util.UUID;

public record CategoriaHomePublicaDto(
    UUID id,
    String identificador,
    String titulo,
    String descricao,
    String destino,
    String imagemPublicaUrl,
    int ordem,
    boolean ativo) {
}
