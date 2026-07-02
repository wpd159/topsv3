package br.com.topsdojob.v3.application.admin.moderacao.dto;

import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;

public record AdminDecidirRevisaoRequestDto(
        AdminDecisaoModeracaoAcao decisao,
        ClassificacaoConteudo classificacaoConteudo,
        String motivo,
        String observacao,
        String requestIdCliente) {
}
