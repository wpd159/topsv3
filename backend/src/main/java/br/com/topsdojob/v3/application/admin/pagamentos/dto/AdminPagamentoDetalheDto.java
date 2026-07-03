package br.com.topsdojob.v3.application.admin.pagamentos.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminPagamentoDetalheDto(
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
        int eventosSanitizadosTotal,
        int webhooksSanitizadosTotal,
        List<String> codigosConsistencia,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm,
        boolean payloadSensivelOculto,
        boolean cobrancaRealDisponivel,
        boolean webhookRealProcessado,
        boolean pixEfiRealExecutado,
        boolean somenteLeitura) {
}
