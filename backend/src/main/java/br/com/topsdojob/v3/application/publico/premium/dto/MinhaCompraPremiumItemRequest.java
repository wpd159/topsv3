package br.com.topsdojob.v3.application.publico.premium.dto;

import java.util.UUID;

public record MinhaCompraPremiumItemRequest(
        String beneficioCodigo,
        Integer duracaoDias,
        UUID opcaoId,
        Integer custoCreditosEsperado) {

    public MinhaCompraPremiumItemRequest(String beneficioCodigo, Integer duracaoDias) {
        this(beneficioCodigo, duracaoDias, null, null);
    }
}
