package br.com.topsdojob.v3.application.admin.premium.dto;

import java.util.List;

public record AdminPremiumAtivarLoteRequest(
        List<AdminPremiumAtivarLoteItemRequest> beneficios,
        String observacao) {
}
