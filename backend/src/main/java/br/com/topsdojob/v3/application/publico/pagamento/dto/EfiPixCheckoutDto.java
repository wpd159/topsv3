package br.com.topsdojob.v3.application.publico.pagamento.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EfiPixCheckoutDto(
        UUID pagamentoId,
        String txid,
        String status,
        BigDecimal valor,
        int quantidadeCreditos,
        OffsetDateTime expiracaoEm,
        String pixCopiaECola,
        String imagemQrCode,
        boolean creditado,
        boolean idempotente) {
}
