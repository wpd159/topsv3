package br.com.topsdojob.v3.application.publico.pagamento.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EfiPagamentoHistoricoDto(
        UUID pagamentoId,
        UUID planoCreditoId,
        String planoNome,
        int quantidadeCreditos,
        BigDecimal valor,
        OffsetDateTime criadoEm,
        OffsetDateTime expiracaoEm,
        String status,
        String ambiente,
        boolean cancelavel,
        String identificacaoSanitizada,
        OffsetDateTime confirmadoEm) {
}
