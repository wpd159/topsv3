package br.com.topsdojob.v3.domain.financeiro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PagamentoConciliacao(
    UUID id,
    UUID pagamentoId,
    UUID movimentoCreditoId,
    FinanceiroTipos.OrigemConciliacao origem,
    FinanceiroTipos.StatusConciliacao status,
    BigDecimal valorConfirmado,
    Integer creditosConfirmados,
    OffsetDateTime aprovadoEm,
    OffsetDateTime creditadoEm,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "pagamento_conciliacao";
}
