package br.com.topsdojob.v3.application.publico.dto;

import org.springframework.data.domain.Page;

public record PaginacaoPublicaDto(
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {

    public PaginacaoPublicaDto {
        if (pagina < 0) {
            throw new IllegalArgumentException("pagina nao pode ser negativa");
        }
        if (tamanho < 1) {
            throw new IllegalArgumentException("tamanho deve ser positivo");
        }
        if (totalItens < 0) {
            throw new IllegalArgumentException("totalItens nao pode ser negativo");
        }
        if (totalPaginas < 0) {
            throw new IllegalArgumentException("totalPaginas nao pode ser negativo");
        }
    }

    public static PaginacaoPublicaDto from(Page<?> page) {
        return new PaginacaoPublicaDto(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
