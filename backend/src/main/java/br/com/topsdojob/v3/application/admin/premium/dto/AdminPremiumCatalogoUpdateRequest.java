package br.com.topsdojob.v3.application.admin.premium.dto;

import java.util.List;

public record AdminPremiumCatalogoUpdateRequest(
        String nome,
        String descricao,
        Boolean ativo,
        Integer ordemExibicao,
        List<AdminPremiumOpcaoUpdateRequest> opcoes) {
}
