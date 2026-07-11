package br.com.topsdojob.v3.application.publico.dto;

import java.util.List;

public record ListaAnunciosPublicaDto(
        List<AnuncioCardPublicoDto> itens,
        PaginacaoPublicaDto paginacao,
        LocalizacaoPublicaDto localidade,
        SeoRotaPublicaDto seo) {
}
