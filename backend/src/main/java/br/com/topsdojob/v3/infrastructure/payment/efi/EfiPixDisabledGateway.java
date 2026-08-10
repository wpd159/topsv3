package br.com.topsdojob.v3.infrastructure.payment.efi;

import br.com.topsdojob.v3.domain.financeiro.FinanceiroTipos.AmbientePagamento;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "efi.pix", name = "enabled", havingValue = "false", matchIfMissing = true)
public class EfiPixDisabledGateway implements EfiPixGateway {

    @Override
    public CobrancaPix criarCobranca(String txid, BigDecimal valor, String descricao) {
        throw indisponivel();
    }

    @Override
    public AmbientePagamento ambiente() {
        throw indisponivel();
    }

    @Override
    public CobrancaPix consultarCobranca(String txid) {
        throw indisponivel();
    }

    @Override
    public CobrancaPix consultarCobrancaSemQrCode(String txid) {
        throw indisponivel();
    }

    @Override
    public CobrancaPix cancelarCobranca(String txid) {
        throw indisponivel();
    }

    @Override
    public void garantirWebhookConfigurado() {
        throw indisponivel();
    }

    private EfiPixGatewayException indisponivel() {
        return new EfiPixGatewayException("integracao Efi nao configurada", true);
    }
}
