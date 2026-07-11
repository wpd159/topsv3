package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;

public record BairroLocalidadePublicaDto(
        String nome,
        String slug,
        long totalAnunciosAtivos,
        OffsetDateTime ultimaAtualizacao) {
}
