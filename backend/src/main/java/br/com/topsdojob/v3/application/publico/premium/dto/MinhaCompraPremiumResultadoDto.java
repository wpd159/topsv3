package br.com.topsdojob.v3.application.publico.premium.dto;

import java.util.List;
import java.util.UUID;

public record MinhaCompraPremiumResultadoDto(
        UUID operacaoId,
        int saldoAnterior,
        int saldoPosterior,
        int totalDebitado,
        List<MinhaAtivacaoPremiumDto> ativacoes,
        boolean idempotente) {
}
