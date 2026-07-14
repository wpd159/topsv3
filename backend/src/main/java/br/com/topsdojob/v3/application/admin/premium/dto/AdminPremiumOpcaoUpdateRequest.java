package br.com.topsdojob.v3.application.admin.premium.dto;

public record AdminPremiumOpcaoUpdateRequest(
        Integer duracaoDias,
        Integer custoCreditos,
        Boolean ativo,
        Integer ordemExibicao) {
}
