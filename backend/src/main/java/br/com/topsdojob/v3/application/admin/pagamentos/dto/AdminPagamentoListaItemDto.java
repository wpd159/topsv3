package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPagamentoListaItemDto(
        UUID id,
        UUID usuarioId,
        String provedorDeclarado,
        String provedorClassificado,
        String metodo,
        String statusInterno,
        String statusOperacional,
        Integer quantidadeCreditos,
        String moeda,
        boolean evidenciaTransacaoPresente,
        String evidenciaTransacaoMascarada,
        boolean evidenciaProvedorPresente,
        boolean creditoVinculado,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        boolean somenteLeitura) {
}
