package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CidadeLocalidadePublicaDto(
        String nome,
        String slug,
        long totalAnunciosAtivos,
        OffsetDateTime ultimaAtualizacao,
        IndexacaoLocalidadePublicaDto indexacao,
        List<BairroLocalidadePublicaDto> bairros) {
}
