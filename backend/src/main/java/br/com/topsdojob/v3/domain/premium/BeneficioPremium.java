package br.com.topsdojob.v3.domain.premium;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BeneficioPremium(
    UUID id,
    String codigo,
    String nome,
    String descricao,
    PremiumTipos.EscopoBeneficio escopo,
    Boolean afetaRanking,
    Boolean ativo,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "beneficio_premium";
}
