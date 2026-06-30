package br.com.topsdojob.v3.domain.financeiro;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PagamentoEvento(
    UUID id,
    UUID pagamentoId,
    FinanceiroTipos.ProvedorPagamento provedor,
    String provedorEventoId,
    String tipoEvento,
    String payloadHash,
    String statusProvedor,
    OffsetDateTime recebidoEm,
    OffsetDateTime processadoEm,
    String resultado) {
  public static final String TABELA = "pagamento_evento";
}
