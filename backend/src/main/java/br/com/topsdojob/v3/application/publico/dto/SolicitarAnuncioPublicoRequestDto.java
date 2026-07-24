package br.com.topsdojob.v3.application.publico.dto;

import java.math.BigDecimal;
import java.util.List;

public record SolicitarAnuncioPublicoRequestDto(
        String whatsapp,
        String uf,
        String cidade,
        String bairro,
        String titulo,
        String descricao,
        BigDecimal preco,
        String categoria,
        List<String> servicos,
        Boolean atendimentoExclusivamenteVirtual,
        Boolean aceiteTermos,
        Boolean confirmacaoIdade) {
}
