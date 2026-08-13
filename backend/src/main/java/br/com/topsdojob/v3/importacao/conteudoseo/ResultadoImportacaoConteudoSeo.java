package br.com.topsdojob.v3.importacao.conteudoseo;

import java.util.UUID;

public record ResultadoImportacaoConteudoSeo(
    UUID execucaoId,
    String status,
    long analisados,
    long importados,
    long publicados,
    long rascunhos,
    long noindex,
    long descartados,
    long quarentena,
    long redirects,
    long processadosNestaChamada,
    long novosRegistrosNestaChamada,
    long restantes) {
}
