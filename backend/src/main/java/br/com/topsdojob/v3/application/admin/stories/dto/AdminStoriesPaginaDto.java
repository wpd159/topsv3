package br.com.topsdojob.v3.application.admin.stories.dto;

import java.util.List;

public record AdminStoriesPaginaDto(
    List<AdminStoryGerenciadoDto> itens,
    int pagina,
    int tamanho,
    long totalElementos,
    int totalPaginas) {
}
