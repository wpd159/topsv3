package br.com.topsdojob.v3.domain.importacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StagingImportacaoReferencia(
    UUID id,
    UUID execucaoId,
    String tabelaStaging,
    String sistemaOrigem,
    String tabelaOrigem,
    String idOrigem,
    String hashOrigem,
    String payloadNormalizadoJson,
    ImportacaoDominioTipos.StatusStaging status,
    String pendenciaCodigo,
    UUID entidadeV3Id,
    OffsetDateTime criadoEm,
    OffsetDateTime processadoEm) {
  public static final String[] TABELAS = {
      "stg_usuario",
      "stg_anuncio",
      "stg_localidade",
      "stg_midia",
      "stg_story",
      "stg_pagamento",
      "stg_credito",
      "stg_premium",
      "stg_url"
  };
}
