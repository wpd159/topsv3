package br.com.topsdojob.v3.application.premium.dto;

import java.util.UUID;

public record PremiumOpcaoDto(
        UUID id,
        int duracaoDias,
        int custoCreditos,
        boolean ativo,
        int ordemExibicao) {
}
