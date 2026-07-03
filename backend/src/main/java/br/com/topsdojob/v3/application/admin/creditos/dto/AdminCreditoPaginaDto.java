package br.com.topsdojob.v3.application.admin.creditos.dto;

import java.util.List;

public record AdminCreditoPaginaDto<T>(
        List<T> itens,
        long total,
        int pagina,
        int tamanho,
        boolean somenteLeitura) {
}
