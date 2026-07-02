package br.com.topsdojob.v3.application.admin.readonly.dto;

import java.util.List;

public record AdminPaginaDto<T>(
        List<T> itens,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
