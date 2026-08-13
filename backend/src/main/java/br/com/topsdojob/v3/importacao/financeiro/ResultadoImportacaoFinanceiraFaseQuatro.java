package br.com.topsdojob.v3.importacao.financeiro;

import java.util.UUID;

public record ResultadoImportacaoFinanceiraFaseQuatro(
    UUID execucaoId,
    String status,
    long pagamentosAnalisados,
    long pagamentosImportados,
    long pagamentosAprovadosImportados,
    long pagamentosQuarentena,
    long pagamentosDescartados,
    long eventosHistoricos,
    long gruposAnalisados,
    long gruposImportados,
    long gruposQuarentena,
    long gruposDescartados,
    long ativacoesAnalisadas,
    long ativacoesImportadas,
    long ativacoesQuarentena,
    long ativacoesDescartadas,
    long carteirasAnalisadas,
    long carteirasComSaldoImportadas,
    long carteirasSaldoZero,
    long carteirasQuarentena,
    long movimentosSaldoInicial,
    long saldoPositivoAnalisado,
    long saldoInicialImportado,
    long saldoPositivoQuarentena,
    long processadosNestaChamada,
    long pagamentosNovosNestaChamada,
    long eventosNovosNestaChamada,
    long gruposNovosNestaChamada,
    long ativacoesNovasNestaChamada,
    long movimentosNovosNestaChamada,
    long restantes) {
}
