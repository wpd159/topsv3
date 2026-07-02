package br.com.topsdojob.v3.application.publico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdadePublicaTokenService {

    public static final String COOKIE_NAME = "topsv3_idade_confirmada";
    static final String LOCAL_FICTITIOUS_SIGNING_VALUE = "valor_local_ficticio_idade";

    private static final Duration TOKEN_TTL = Duration.ofHours(6);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String signingValue;
    private final boolean local;

    public IdadePublicaTokenService(
            @Value("${app.age-gate.signing-value:}") String configuredSigningValue,
            @Value("${app.env:nao_configurado}") String appEnv) {
        this.local = isLocal(appEnv);
        this.signingValue = resolveSigningValue(configuredSigningValue, this.local);
    }

    public String emitir(OffsetDateTime now) {
        OffsetDateTime issuedAt = now.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime expiresAt = issuedAt.plus(TOKEN_TTL);
        String payload = "v1|" + issuedAt.toEpochSecond() + "|" + expiresAt.toEpochSecond();
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + assinatura(encodedPayload);
    }

    public Optional<OffsetDateTime> validar(String valorAssinado, OffsetDateTime now) {
        if (valorAssinado == null || valorAssinado.isBlank() || !valorAssinado.contains(".")) {
            return Optional.empty();
        }
        String[] parts = valorAssinado.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        String expectedSignature = assinatura(parts[0]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        String[] payloadParts = payload.split("\\|", -1);
        if (payloadParts.length != 3 || !"v1".equals(payloadParts[0])) {
            return Optional.empty();
        }
        try {
            long expiresAtEpoch = Long.parseLong(payloadParts[2]);
            OffsetDateTime expiresAt = OffsetDateTime.ofInstant(
                    java.time.Instant.ofEpochSecond(expiresAtEpoch),
                    ZoneOffset.UTC);
            if (!expiresAt.isAfter(now.withOffsetSameInstant(ZoneOffset.UTC))) {
                return Optional.empty();
            }
            return Optional.of(expiresAt);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return TOKEN_TTL;
    }

    public boolean cookieSecure() {
        return !local;
    }

    private String assinatura(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingValue.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("assinatura de idade indisponivel", exception);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isLocal(String appEnv) {
        String normalizedEnv = appEnv == null ? "nao_configurado" : appEnv.trim();
        return normalizedEnv.equalsIgnoreCase("local");
    }

    private static String resolveSigningValue(String configuredValue, boolean local) {
        String normalizedValue = configuredValue == null ? "" : configuredValue.trim();
        if (local && normalizedValue.isBlank()) {
            return LOCAL_FICTITIOUS_SIGNING_VALUE;
        }
        if (!local && (normalizedValue.isBlank() || normalizedValue.equals(LOCAL_FICTITIOUS_SIGNING_VALUE))) {
            throw new IllegalStateException("APP_AGE_GATE_SIGNING_VALUE obrigatorio fora do ambiente local");
        }
        return normalizedValue;
    }
}
