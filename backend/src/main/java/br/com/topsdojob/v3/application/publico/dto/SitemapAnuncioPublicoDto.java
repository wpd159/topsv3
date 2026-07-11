package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;

public record SitemapAnuncioPublicoDto(
        String slug,
        String estadoUf,
        String cidadeSlug,
        String bairroSlug,
        OffsetDateTime atualizadoEm,
        boolean publico,
        boolean indexavel) {
}
