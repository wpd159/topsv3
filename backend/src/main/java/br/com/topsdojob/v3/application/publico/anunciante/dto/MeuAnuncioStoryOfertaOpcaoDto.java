package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.UUID;

public record MeuAnuncioStoryOfertaOpcaoDto(
    UUID opcaoId,
    int duracaoDias,
    int custoCreditos,
    int saldoAtual,
    int saldoAposCompra,
    int creditosFaltantes,
    int ordemExibicao) {
}
