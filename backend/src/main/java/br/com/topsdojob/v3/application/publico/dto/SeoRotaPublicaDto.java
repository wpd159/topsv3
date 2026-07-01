package br.com.topsdojob.v3.application.publico.dto;

public record SeoRotaPublicaDto(
        String title,
        String description,
        String canonicalPath,
        String robots,
        String tipoRota,
        boolean indexavelFuturo) {
}
