package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.UUID;

public record MeuAnuncioStoryAtivacaoDto(
    UUID ativacaoId,
    int custoCreditos,
    int saldoAnterior,
    int saldoAtual,
    int duracaoHoras,
    boolean idempotente) {
}
