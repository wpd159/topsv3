package br.com.topsdojob.v3.infrastructure.payment.efi;

public class EfiPixGatewayException extends RuntimeException {

    private final boolean configuracao;
    private final Integer httpStatus;
    private final String providerCode;
    private final boolean qrCode;

    public EfiPixGatewayException(String message, boolean configuracao) {
        this(message, configuracao, null, null, false, null);
    }

    public EfiPixGatewayException(String message, boolean configuracao, Throwable cause) {
        this(message, configuracao, null, null, false, cause);
    }

    public EfiPixGatewayException(String message, boolean configuracao, int httpStatus) {
        this(message, configuracao, httpStatus, null, false, null);
    }

    public EfiPixGatewayException(
            String message,
            boolean configuracao,
            int httpStatus,
            String providerCode) {
        this(message, configuracao, httpStatus, providerCode, false, null);
    }

    private EfiPixGatewayException(
            String message,
            boolean configuracao,
            Integer httpStatus,
            String providerCode,
            boolean qrCode,
            Throwable cause) {
        super(message, cause);
        this.configuracao = configuracao;
        this.httpStatus = httpStatus;
        this.providerCode = providerCode != null && providerCode.matches("[a-z0-9_]{1,80}")
                ? providerCode : null;
        this.qrCode = qrCode;
    }

    public boolean isConfiguracao() {
        return configuracao;
    }

    public boolean isCobrancaNaoEncontrada() {
        return Integer.valueOf(404).equals(httpStatus);
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public boolean isStatusCobrancaInvalido() {
        return "status_cobranca_invalido".equals(providerCode);
    }

    public boolean isQrCode() {
        return qrCode;
    }

    public static EfiPixGatewayException qrCode(EfiPixGatewayException cause) {
        return new EfiPixGatewayException(
                "falha ao carregar QR Code Pix",
                false,
                cause.getHttpStatus(),
                cause.getProviderCode(),
                true,
                cause);
    }
}
