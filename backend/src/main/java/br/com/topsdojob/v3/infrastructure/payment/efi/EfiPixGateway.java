package br.com.topsdojob.v3.infrastructure.payment.efi;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface EfiPixGateway {

    CobrancaPix criarCobranca(String txid, BigDecimal valor, String descricao);

    CobrancaPix consultarCobranca(String txid);

    void garantirWebhookConfigurado();

    record CobrancaPix(
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
