package br.com.topsdojob.v3.application.operacional.midia;

import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MidiaRestritaPreviewIdentity {

  private static final String DERIVATION_VERSION = "v1";
  private static final String DERIVATION_DIRECTORY =
      "restritas-borradas/" + DERIVATION_VERSION + "/";

  private final R2StorageProperties properties;

  public MidiaRestritaPreviewIdentity(R2StorageProperties properties) {
    this.properties = properties;
  }

  public String prefixoPreviews() {
    if (properties == null || properties.getPublicMediaPrefix() == null
        || properties.getPublicMediaPrefix().isBlank()) {
      throw new IllegalStateException("Prefixo publico de midia nao configurado");
    }
    return properties.getPublicMediaPrefix() + DERIVATION_DIRECTORY;
  }

  public String versaoPipeline() {
    return DERIVATION_VERSION;
  }

  public boolean isPreviewKey(String key) {
    return key != null && key.contains("/" + DERIVATION_DIRECTORY);
  }

  public String chavePublica(ArquivoMidiaEntity arquivo) {
    String key = chavePublicaOuNula(arquivo);
    if (key == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "midia restrita sem identidade canonica");
    }
    return key;
  }

  public String chavePublicaOuNula(ArquivoMidiaEntity arquivo) {
    return arquivo == null ? null : chavePublicaOuNula(arquivo.getId(), arquivo.getSha256());
  }

  /** Pure scalar overload; producers retain the same canonical algorithm. */
  public String chavePublicaOuNula(java.util.UUID id, String sha256) {
    if (id == null || properties == null
        || properties.getPublicMediaPrefix() == null
        || properties.getPublicMediaPrefix().isBlank()) {
      return null;
    }
    String checksum = sha256 == null
        ? "sem-checksum"
        : sha256.trim().toLowerCase(Locale.ROOT);
    String identificadorDerivado = sha256(
        (id + ":" + checksum + ":" + DERIVATION_VERSION)
            .getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    return properties.getPublicMediaPrefix()
        + DERIVATION_DIRECTORY
        + identificadorDerivado
        + ".jpg";
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }
}
