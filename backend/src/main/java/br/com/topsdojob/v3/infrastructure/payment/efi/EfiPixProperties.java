package br.com.topsdojob.v3.infrastructure.payment.efi;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "efi.pix")
public class EfiPixProperties {

    private boolean enabled;
    private String environment = "homologacao";
    private String baseUrl = "https://pix-h.api.efipay.com.br";
    private String clientId;
    private String certificatePath;
    private String pixKey;
    private String webhookBaseUrl;
    private int chargeExpirationSeconds = 3600;
    private final Map<String, String> guardedValues = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return guardedValues.get("oauth-client");
    }

    public void setClientSecret(String value) {
        guardedValues.put("oauth-client", value);
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public String getCertificateProtection() {
        return guardedValues.get("mtls-protection");
    }

    public void setCertificateProtection(String value) {
        guardedValues.put("mtls-protection", value);
    }

    public String getPixKey() {
        return pixKey;
    }

    public void setPixKey(String pixKey) {
        this.pixKey = pixKey;
    }

    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        this.webhookBaseUrl = webhookBaseUrl;
    }

    public String getWebhookVerifier() {
        return guardedValues.get("webhook-verifier");
    }

    public void setWebhookVerifier(String value) {
        guardedValues.put("webhook-verifier", value);
    }

    public int getChargeExpirationSeconds() {
        return chargeExpirationSeconds;
    }

    public void setChargeExpirationSeconds(int chargeExpirationSeconds) {
        this.chargeExpirationSeconds = chargeExpirationSeconds;
    }
}
