package br.com.topsdojob.v3.application.publico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetricaPublicaHashService {

    static final String LOCAL_FICTITIOUS_SALT = "valor_local_ficticio";

    private final String salt;

    public MetricaPublicaHashService(
            @Value("${app.event.hash-salt:}") String salt,
            @Value("${app.env:local}") String appEnv) {
        this.salt = resolveSalt(salt, appEnv);
    }

    public String hash(String escopo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (salt + "|" + escopo + "|" + valor.trim()).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private String resolveSalt(String configuredSalt, String appEnv) {
        String normalizedEnv = appEnv == null ? "local" : appEnv.trim();
        boolean local = normalizedEnv.equalsIgnoreCase("local");
        String normalizedSalt = configuredSalt == null ? "" : configuredSalt.trim();
        if (local && normalizedSalt.isBlank()) {
            return LOCAL_FICTITIOUS_SALT;
        }
        if (!local && (normalizedSalt.isBlank() || normalizedSalt.equals(LOCAL_FICTITIOUS_SALT))) {
            throw new IllegalStateException("APP_EVENT_HASH_SALT obrigatorio fora do ambiente local");
        }
        return normalizedSalt;
    }
}
