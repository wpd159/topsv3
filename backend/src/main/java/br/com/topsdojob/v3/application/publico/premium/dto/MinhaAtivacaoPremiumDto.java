package br.com.topsdojob.v3.application.publico.premium.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MinhaAtivacaoPremiumDto(
        UUID id,
        UUID anuncioId,
        String anuncioSlug,
        String anuncioTitulo,
        String beneficioCodigo,
        String beneficioNome,
        String status,
        int custoCreditos,
        int duracaoDias,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm,
        String efeitoPublico,
        String motivoIneficacia) {
}
