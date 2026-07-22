package br.com.topsdojob.v3.application.publico.anunciante.dto;

public record MeuAnuncioAcoesDto(
        boolean pausar,
        boolean reativar,
        boolean remover) {
}
