package br.com.topsdojob.v3.application.premium.dto;

import java.time.OffsetDateTime;
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
        OffsetDateTime atualizadoEm,
        List<PremiumOpcaoDto> opcoes) {
}
