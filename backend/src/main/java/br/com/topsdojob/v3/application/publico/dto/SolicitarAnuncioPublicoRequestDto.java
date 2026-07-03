package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;

public record SolicitarAnuncioPublicoRequestDto(
        String nomeExibicao,
        String email,
        String whatsapp,
        String uf,
        String cidade,
        String bairro,
        String titulo,
        String descricao,
        BigDecimal preco,
        String categoria,
        Boolean aceiteTermos,
        Boolean confirmacaoIdade) {
}
