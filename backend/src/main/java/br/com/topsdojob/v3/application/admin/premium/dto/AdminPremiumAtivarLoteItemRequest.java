package br.com.topsdojob.v3.application.admin.premium.dto;

import java.util.UUID;

public record AdminPremiumAtivarLoteItemRequest(
        UUID beneficioId,
        Integer duracaoDias) {
}
