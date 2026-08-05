package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.UUID;

public record MinhaContaStoryAtivacaoDto(
    UUID ativacaoId,
    String modoConteudo,
    UUID anuncioId,
    int custoCreditos,
    int saldoAnterior,
    int saldoAtual,
    int duracaoHoras,
    boolean idempotente) {
}
