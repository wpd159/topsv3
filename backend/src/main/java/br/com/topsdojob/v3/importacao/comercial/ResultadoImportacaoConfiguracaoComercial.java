package br.com.topsdojob.v3.importacao.comercial;

import java.util.UUID;

public record ResultadoImportacaoConfiguracaoComercial(
    UUID execucaoId,
    String status,
    long produtosAnalisados,
    long produtosImportadosAtivos,
    long produtosImportadosInativos,
    long produtosHistoricos,
    long produtosDescartados,
    long produtosQuarentena,
    long opcoesAnalisadas,
    long opcoesImportadas,
    long opcoesQuarentena,
    long pacotesAnalisados,
    long pacotesImportadosAtivos,
    long pacotesImportadosInativos,
    long pacotesDescartados,
    long pacotesQuarentena,
    long storiesAnalisadas,
    long storiesImportadas,
    long storiesQuarentena,
    long processadosNestaChamada,
    long novosRegistrosNestaChamada,
    long restantes) {
}
