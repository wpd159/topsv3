package br.com.topsdojob.v3.application.admin.premium.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminBeneficioAnuncioDto(
        UUID id,
        String beneficioCodigo,
        String beneficioNome,
        String escopo,
        String statusOriginal,
        String statusCalculado,
        OffsetDateTime inicioEm,
        OffsetDateTime fimEm,
        boolean venceEmBreve,
        boolean grupoVinculado,
        UUID grupoId,
        String grupoTipo,
        String grupoStatus,
        OffsetDateTime grupoFimEm,
        List<String> codigosConsistencia,
        boolean inconsistente,
        boolean somenteLeitura) {
}
