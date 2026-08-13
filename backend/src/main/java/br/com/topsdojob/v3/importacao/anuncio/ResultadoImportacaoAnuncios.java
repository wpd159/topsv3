package br.com.topsdojob.v3.importacao.anuncio;

import java.util.UUID;

public record ResultadoImportacaoAnuncios(
    UUID execucaoId,
    String status,
    long analisados,
    long importados,
    long publicos,
    long emRevisao,
    long quarentena,
    long descartados,
    long revisoesImportadas,
    long filhosImportados,
    long processadosNestaChamada,
    long novosAnunciosNestaChamada,
    long novasRevisoesNestaChamada,
    long novosFilhosNestaChamada,
    long restantes) {
}
