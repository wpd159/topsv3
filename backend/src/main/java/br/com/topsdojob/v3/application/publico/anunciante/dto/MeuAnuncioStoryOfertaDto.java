package br.com.topsdojob.v3.application.publico.anunciante.dto;

public record MeuAnuncioStoryOfertaDto(
    String estado,
    Boolean configurada,
    Boolean ativo,
    Integer duracaoHoras,
    Integer custoCreditos,
    Integer saldoAtual,
    Integer saldoProjetado,
    Integer deficit,
    MeuAnuncioStoryDto storyAtivo,
    MeuAnuncioStoryDireitoDto direitoDisponivel,
    Long versaoConfiguracao) {
}