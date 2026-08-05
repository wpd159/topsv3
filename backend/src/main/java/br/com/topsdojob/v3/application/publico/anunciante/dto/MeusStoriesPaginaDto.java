package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.List;

public record MeusStoriesPaginaDto(
    List<MeuStoryGerenciadoDto> itens,
    int pagina,
    int tamanho,
    long totalElementos,
    int totalPaginas) {
}
