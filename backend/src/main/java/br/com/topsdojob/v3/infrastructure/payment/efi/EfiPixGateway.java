package br.com.topsdojob.v3.infrastructure.payment.efi;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface EfiPixGateway {

    CobrancaPix criarCobranca(String txid, BigDecimal valor, String descricao);

    AmbientePagamento ambiente();

    CobrancaPix consultarCobranca(String txid);

    CobrancaPix consultarCobrancaSemQrCode(String txid);

    CobrancaPix cancelarCobranca(String txid);

    void garantirWebhookConfigurado();

    record CobrancaPix(
            AmbientePagamento ambiente,
            String txid,
            String status,
            String identificadorLocalizacao,
            BigDecimal valorOriginal,
            BigDecimal valorRecebido,
            OffsetDateTime expiracaoEm,
            String pixCopiaECola,
            String imagemQrCode) {
    }
}
