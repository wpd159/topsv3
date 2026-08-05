package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record ListaAnunciosUsuarioPublicaDto(
        String username,
        String displayUsername,
        List<AnuncioCardPublicoDto> itens,
        PaginacaoPublicaDto paginacao) {
}
