package br.com.topsdojob.v3.infrastructure.payment.efi;

public class EfiPixGatewayException extends RuntimeException {

    private final boolean configuracao;

    public EfiPixGatewayException(String message, boolean configuracao) {
        super(message);
        this.configuracao = configuracao;
    }

    public EfiPixGatewayException(String message, boolean configuracao, Throwable cause) {
        super(message, cause);
        this.configuracao = configuracao;
    }

    public boolean isConfiguracao() {
        return configuracao;
    }
}
