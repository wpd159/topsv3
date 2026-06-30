package br.com.topsdojob.v3.domain.importacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportacaoExecucao(
    UUID id,
    String sistemaOrigem,
    ImportacaoDominioTipos.StatusExecucao status,
    OffsetDateTime iniciadoEm,
    OffsetDateTime finalizadoEm,
    UUID solicitadoPor,
    String resumoJson,
    OffsetDateTime criadoEm) {
  public static final String TABELA = "importacao_execucao";
}
