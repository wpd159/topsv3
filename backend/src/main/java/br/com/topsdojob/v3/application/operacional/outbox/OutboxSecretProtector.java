package br.com.topsdojob.v3.application.operacional.outbox;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxSecretProtector {
  private static final String VERSION = "v1";
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final String configuredKey;
  private final SecureRandom random = new SecureRandom();

  public OutboxSecretProtector(
      @Value("${app.outbox.email.encryption-key:}") String configuredKey) {
    this.configuredKey = configuredKey == null ? "" : configuredKey.trim();
  }

  public String protect(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("segredo de comunicacao obrigatorio");
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return VERSION + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
          + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("falha ao proteger segredo da comunicacao", exception);
    }
  }

  public String reveal(String protectedValue) {
    try {
      String[] parts = protectedValue == null ? new String[0] : protectedValue.split(":", 3);
      if (parts.length != 3 || !VERSION.equals(parts[0])) {
        throw new IllegalArgumentException("envelope protegido invalido");
      }
      byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
      byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException | java.security.ProviderException exception) {
      throw new IllegalStateException("falha ao abrir segredo da comunicacao", exception);
    }
  }

  private SecretKeySpec key() {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(configuredKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("OUTBOX_PAYLOAD_ENCRYPTION_KEY deve usar Base64", exception);
    }
    if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
      throw new IllegalStateException("OUTBOX_PAYLOAD_ENCRYPTION_KEY deve representar 16, 24 ou 32 bytes");
    }
    return new SecretKeySpec(decoded, "AES");
  }
}
