package br.com.topsdojob.v3.application.publico.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class ComplianceSignedCookieService {

  public static final String GLOBAL_COOKIE = "age_gate_accepted";
  public static final String SESSION_COOKIE = "visitor_session_id";
  public static final String ACCESS_COOKIE = "visitor_access_token";
  public static final String EXPLICIT_ACCESS_COOKIE = "visitor_explicit_access_token";

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String LOCAL_SIGNING_VALUE = "valor_local_ficticio_idade";

  private final String signingValue;
  private final boolean secure;

  public ComplianceSignedCookieService(
      @Value("${app.age-gate.signing-value:}") String configuredSigningValue,
      @Value("${app.env:nao_configurado}") String appEnv) {
    boolean local = "local".equalsIgnoreCase(appEnv == null ? "" : appEnv.trim());
    String normalized = configuredSigningValue == null ? "" : configuredSigningValue.trim();
    if (local && normalized.isBlank()) {
      normalized = LOCAL_SIGNING_VALUE;
    }
    if (!local && (normalized.isBlank() || LOCAL_SIGNING_VALUE.equals(normalized))) {
      throw new IllegalStateException("APP_AGE_GATE_SIGNING_VALUE obrigatorio fora do ambiente local");
    }
    this.signingValue = normalized;
    this.secure = !local;
  }

  public String emitir(
      TipoTokenAssinado tipo,
      UUID id,
      OffsetDateTime emitidoEm,
      OffsetDateTime expiraEm,
      String escopo,
      String nivel) {
    String payload = String.join(
        "|",
        "v2",
        tipo.name(),
        id.toString(),
        Long.toString(emitidoEm.toEpochSecond()),
        Long.toString(expiraEm.toEpochSecond()),
        normalizarCampo(escopo),
        normalizarCampo(nivel));
    String encoded = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + assinatura(encoded);
  }

  public Optional<TokenAssinado> validar(
      String valor,
      TipoTokenAssinado tipoEsperado,
      OffsetDateTime agora) {
    if (valor == null || valor.isBlank()) {
      return Optional.empty();
    }
    String[] parts = valor.split("\\.", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      return Optional.empty();
    }
    String expected = assinatura(parts[0]);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        parts[1].getBytes(StandardCharsets.UTF_8))) {
      return Optional.empty();
    }
    try {
      String payload = new String(
          Base64.getUrlDecoder().decode(parts[0]),
          StandardCharsets.UTF_8);
      String[] fields = payload.split("\\|", -1);
      if (fields.length != 7
          || !"v2".equals(fields[0])
          || !tipoEsperado.name().equals(fields[1])) {
        return Optional.empty();
      }
      TokenAssinado estadoAssinado = new TokenAssinado(
          TipoTokenAssinado.valueOf(fields[1]),
          UUID.fromString(fields[2]),
          instante(fields[3]),
          instante(fields[4]),
          restaurarCampo(fields[5]),
          restaurarCampo(fields[6]));
      if (!estadoAssinado.expiraEm().isAfter(agora.withOffsetSameInstant(ZoneOffset.UTC))) {
        return Optional.empty();
      }
      return Optional.of(estadoAssinado);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  public ResponseCookie cookie(String nome, String valor, Duration ttl) {
    return ResponseCookie.from(nome, valor)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(ttl)
        .build();
  }

  public ResponseCookie expirar(String nome) {
    return ResponseCookie.from(nome, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  public boolean cookieSecure() {
    return secure;
  }

  private OffsetDateTime instante(String epoch) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(Long.parseLong(epoch)),
        ZoneOffset.UTC);
  }

  private String normalizarCampo(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private String restaurarCampo(String value) {
    return "-".equals(value) ? null : value;
  }

  private String assinatura(String encoded) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(
          signingValue.getBytes(StandardCharsets.UTF_8),
          HMAC_ALGORITHM));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(encoded.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("assinatura do age gate indisponivel", exception);
    }
  }

  public enum TipoTokenAssinado {
    GLOBAL,
    SESSION,
    ACCESS,
    EXPLICIT
  }

  public record TokenAssinado(
      TipoTokenAssinado tipo,
      UUID id,
      OffsetDateTime emitidoEm,
      OffsetDateTime expiraEm,
      String escopo,
      String nivel) {
  }
}
