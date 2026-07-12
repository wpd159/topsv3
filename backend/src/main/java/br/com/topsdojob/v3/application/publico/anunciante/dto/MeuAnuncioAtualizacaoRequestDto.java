package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.math.BigDecimal;
import java.util.List;

public record MeuAnuncioAtualizacaoRequestDto(
        String titulo,
        String descricao,
        String categoria,
        BigDecimal preco,
        String uf,
        String cidade,
        String bairro,
        List<String> locaisAtendimento,
        List<String> servicos,
        String whatsapp) {
}
