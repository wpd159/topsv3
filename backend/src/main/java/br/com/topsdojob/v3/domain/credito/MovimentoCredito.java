package br.com.topsdojob.v3.domain.credito;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MovimentoCredito(
    UUID id,
    UUID usuarioId,
    CreditoTipos.TipoMovimento tipo,
    CreditoTipos.Direcao direcao,
    Integer quantidade,
    Integer saldoAntes,
    Integer saldoDepois,
    CreditoTipos.OrigemMovimento origem,
    String referenciaTipo,
    UUID referenciaId,
    String idempotencyKey,
    UUID atorUsuarioId,
    String observacao,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "movimento_credito";
}
