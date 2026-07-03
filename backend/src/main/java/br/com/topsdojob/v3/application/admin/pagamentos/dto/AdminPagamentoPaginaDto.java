package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.util.List;

public record AdminPagamentoPaginaDto<T>(
        List<T> itens,
        long total,
        int pagina,
        int tamanho,
        boolean somenteLeitura) {
}
