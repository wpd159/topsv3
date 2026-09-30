package br.com.topsdojob.v3.application.admin.moderacao.dto;

import java.util.UUID;

/** A business attempt, independent of the HTTP request ID assigned by a proxy. */
public record AdminAprovarAnuncioRequestDto(
        UUID operacaoIdCliente,
        Integer versaoAnuncioEsperada,
        UUID revisaoIdEsperada) {
}
