package br.com.topsdojob.v3.domain.premium;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GrupoAtivacaoBeneficio(
    UUID id,
    PremiumTipos.TipoGrupoAtivacao tipo,
    PremiumTipos.OrigemBeneficio origem,
    UUID usuarioId,
    UUID anuncioId,
    UUID atorUsuarioId,
    String campanhaCodigo,
    OffsetDateTime validadeInicioEm,
    OffsetDateTime validadeFimEm,
    PremiumTipos.StatusGrupoAtivacao status,
    String idempotencyKey,
    String observacao,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "grupo_ativacao_beneficio";
}
