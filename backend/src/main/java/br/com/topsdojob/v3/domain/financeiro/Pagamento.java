package br.com.topsdojob.v3.domain.financeiro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Pagamento(
    UUID id,
    UUID usuarioId,
    UUID planoCreditoId,
    FinanceiroTipos.ProvedorPagamento provedor,
    FinanceiroTipos.MetodoPagamento metodo,
    String txid,
    String identificadorProvedor,
    BigDecimal valor,
    String moeda,
    Integer quantidadeCreditos,
    FinanceiroTipos.StatusInternoPagamento statusInterno,
    String statusProvedor,
    OffsetDateTime expiracaoEm,
    OffsetDateTime aprovadoEm,
    OffsetDateTime canceladoEm,
    OffsetDateTime creditadoEm,
    String idempotencyKey,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "pagamento";
}
