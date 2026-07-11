package br.com.topsdojob.v3.application.publico.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EstadoLocalidadePublicaDto(
        String uf,
        String nome,
        long totalAnunciosAtivos,
        OffsetDateTime ultimaAtualizacao,
        List<CidadeLocalidadePublicaDto> cidades) {
}
