package br.com.topsdojob.v3.application.admin.anuncio.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminAnuncioAtualizacaoRequest(
        String titulo,
        String descricao,
        String categoria,
        BigDecimal preco,
        String uf,
        String cidade,
        String bairro,
        String enderecoResumido,
        List<String> locaisAtendimento,
        List<String> servicos,
        String whatsapp,
        boolean atendimentoExclusivamenteVirtual) {
}
