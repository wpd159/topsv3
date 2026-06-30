package br.com.topsdojob.v3.domain.importacao;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportacaoMapeamento(
    UUID id,
    UUID execucaoId,
    String sistemaOrigem,
    String tabelaOrigem,
    String idOrigem,
    String hashOrigem,
    String entidadeTipo,
    UUID entidadeV3Id,
    ImportacaoDominioTipos.StatusMapeamento status,
    OffsetDateTime criadoEm,
    OffsetDateTime atualizadoEm) {
  public static final String TABELA = "importacao_mapeamento";
}
