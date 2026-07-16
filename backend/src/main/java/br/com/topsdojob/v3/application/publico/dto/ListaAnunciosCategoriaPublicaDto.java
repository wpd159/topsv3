package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record ListaAnunciosCategoriaPublicaDto(
    List<AnuncioCardPublicoDto> itens,
    PaginacaoPublicaDto paginacao,
    String categoria) {
}
