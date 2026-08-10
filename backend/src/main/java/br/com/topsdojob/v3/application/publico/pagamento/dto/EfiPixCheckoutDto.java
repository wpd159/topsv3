package br.com.topsdojob.v3.application.publico.pagamento.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EfiPixCheckoutDto(
        UUID pagamentoId,
        UUID planoCreditoId,
        String planoNome,
        String identificacaoSanitizada,
        String status,
        String ambiente,
        boolean cancelavel,
        BigDecimal valor,
        int quantidadeCreditos,
        OffsetDateTime criadoEm,
        OffsetDateTime expiracaoEm,
        OffsetDateTime confirmadoEm,
        String pixCopiaECola,
        String imagemQrCode,
        boolean creditado,
        boolean idempotente) {
}
