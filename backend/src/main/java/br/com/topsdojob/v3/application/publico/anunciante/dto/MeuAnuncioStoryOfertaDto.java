package br.com.topsdojob.v3.application.publico.anunciante.dto;

import java.util.List;

public record MeuAnuncioStoryOfertaDto(
    String estado,
    Integer saldoCreditos,
    MeuAnuncioStoryDto storyAtivo,
    MeuAnuncioStoryDireitoDto direitoDisponivel,
    String beneficioCodigo,
    String nome,
    String descricao,
    List<MeuAnuncioStoryOfertaOpcaoDto> opcoes) {
}
