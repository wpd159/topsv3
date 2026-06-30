package br.com.topsdojob.v3.domain.financeiro;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PagamentoWebhook(
    UUID id,
    FinanceiroTipos.ProvedorPagamento provedor,
    String eventoId,
    String txid,
    String payloadHash,
    String origemIpHash,
    FinanceiroTipos.ValidacaoWebhook validacaoResultado,
    OffsetDateTime recebidoEm,
    OffsetDateTime processadoEm,
    String resultado,
    String erroResumido,
    Integer tentativas) {
  public static final String TABELA = "pagamento_webhook";
}
