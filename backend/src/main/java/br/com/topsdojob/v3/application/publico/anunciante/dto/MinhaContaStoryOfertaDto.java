package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.UUID;

public record MinhaContaStoryOfertaDto(
    String modoConteudo,
    UUID anuncioId,
    String estado,
    Boolean configurada,
    Boolean ativo,
    Integer duracaoHoras,
    Integer custoCreditos,
    Integer saldoAtual,
    Integer saldoProjetado,
    Integer deficit,
    MinhaContaStoryDto storyAtivo,
    MinhaContaStoryDireitoDto direitoDisponivel,
    Long versaoConfiguracao) {
}
