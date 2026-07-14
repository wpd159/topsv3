package br.com.topsdojob.v3.application.publico.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MinhaAtivacaoPremiumDto(
        UUID id,
        String beneficioCodigo,
        String beneficioNome,
        String status,
        int custoCreditos,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm) {
}
