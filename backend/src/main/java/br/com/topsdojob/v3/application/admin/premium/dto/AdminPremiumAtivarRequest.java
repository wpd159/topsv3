package br.com.topsdojob.v3.application.admin.premium.dto;

import java.util.UUID;

public record AdminPremiumAtivarRequest(
        UUID beneficioId,
        Integer duracaoDias,
        String observacao) {
}
