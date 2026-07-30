package br.com.topsdojob.v3.application.publico.auth.dto;

public record MinhaContaExcluirRequestDto(
        String senhaAtual,
        String confirmacao,
        boolean cienteConsequencias) {
}
