package br.com.topsdojob.v3.application.publico.premium.dto;

import java.util.List;

public record MinhaCompraPremiumRequest(
        String anuncioSlug,
        List<MinhaCompraPremiumItemRequest> itens) {
}
