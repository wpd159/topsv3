package br.com.topsdojob.v3.infrastructure.payment.efi;

public class EfiPixGatewayException extends RuntimeException {

    private final boolean configuracao;
    private final Integer httpStatus;

    public EfiPixGatewayException(String message, boolean configuracao) {
        this(message, configuracao, null, null);
    }

    public EfiPixGatewayException(String message, boolean configuracao, Throwable cause) {
        this(message, configuracao, null, cause);
    }

    public EfiPixGatewayException(String message, boolean configuracao, int httpStatus) {
        this(message, configuracao, httpStatus, null);
    }

    private EfiPixGatewayException(
            String message,
            boolean configuracao,
            Integer httpStatus,
            Throwable cause) {
        super(message, cause);
        this.configuracao = configuracao;
        this.httpStatus = httpStatus;
    }

    public boolean isConfiguracao() {
        return configuracao;
    }

    public boolean isCobrancaNaoEncontrada() {
        return Integer.valueOf(404).equals(httpStatus);
    }
}
