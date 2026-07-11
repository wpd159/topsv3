package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgregadoCidadePublicaDto(
        String estadoUf,
        String estadoNome,
        String cidadeNome,
        String cidadeSlug,
        long totalAnunciosAtivos,
        OffsetDateTime ultimaAtualizacao,
        List<BairroLocalidadePublicaDto> bairros,
        List<CategoriaCidadePublicaDto> categorias,
        List<CidadeLocalidadePublicaDto> cidadesRelacionadas) {
}
