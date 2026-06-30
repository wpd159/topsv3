package br.com.topsdojob.v3.domain.financeiro;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanoCredito(
    UUID id,
    String codigo,
    String nome,
    Integer quantidadeCreditos,
    BigDecimal valor,
    String moeda,
    Boolean ativo,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "plano_credito";
}
