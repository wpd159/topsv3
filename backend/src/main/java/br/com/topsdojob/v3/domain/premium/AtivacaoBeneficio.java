package br.com.topsdojob.v3.domain.premium;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AtivacaoBeneficio(
    UUID id,
    UUID beneficioId,
    UUID opcaoId,
    UUID usuarioId,
    UUID anuncioId,
    UUID grupoAtivacaoId,
    PremiumTipos.OrigemBeneficio origem,
    UUID atorUsuarioId,
    String campanhaCodigo,
    OffsetDateTime inicioEm,
    OffsetDateTime fimEm,
    PremiumTipos.StatusAtivacao status,
    Integer custoCreditosSnapshot,
    BigDecimal precoSnapshot,
    String idempotencyKey,
    OffsetDateTime revogadaEm,
    String motivoRevogacao,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "ativacao_beneficio";
}
