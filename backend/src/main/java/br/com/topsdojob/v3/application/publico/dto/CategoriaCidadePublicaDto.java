package br.com.topsdojob.v3.application.publico.dto;

public record CategoriaCidadePublicaDto(
        String codigo,
        String nome,
        long totalAnunciosAtivos) {
}
