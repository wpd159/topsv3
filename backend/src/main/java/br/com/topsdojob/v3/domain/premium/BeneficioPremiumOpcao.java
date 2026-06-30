package br.com.topsdojob.v3.domain.premium;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BeneficioPremiumOpcao(
    UUID id,
    UUID beneficioId,
    Integer duracaoDias,
    Integer custoCreditos,
    BigDecimal precoReferencia,
    Integer versaoRegra,
    Boolean ativo,
    OffsetDateTime vigenciaInicioEm,
    OffsetDateTime vigenciaFimEm,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "beneficio_premium_opcao";
}
