package br.com.topsdojob.v3.application.premium.dto;

import java.util.List;
import java.util.UUID;

public record PremiumCatalogoDto(
        UUID id,
        String codigo,
        String nome,
        String descricao,
        String escopo,
        boolean afetaRanking,
        boolean ativo,
        int ordemExibicao,
        List<PremiumOpcaoDto> opcoes) {
}
