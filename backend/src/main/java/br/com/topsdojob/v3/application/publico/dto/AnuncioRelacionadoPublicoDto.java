package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AnuncioRelacionadoPublicoDto(
        UUID id,
        String slug,
        String titulo,
        Integer idade,
        BigDecimal preco,
        String cidadeNome,
        String estadoUf,
        List<MidiaPublicaDto> midias) {
}
