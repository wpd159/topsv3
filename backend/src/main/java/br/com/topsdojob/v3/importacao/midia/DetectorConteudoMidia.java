package br.com.topsdojob.v3.importacao.midia;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

final class DetectorConteudoMidia {

  Optional<Detectado> detectar(byte[] content) {
    if (content == null || content.length < 4) {
      return Optional.empty();
    }
    if (jpeg(content)) {
      return Optional.of(new Detectado("image/jpeg", "jpg"));
    }
    if (png(content)) {
      return Optional.of(new Detectado("image/png", "png"));
    }
    if (webp(content)) {
      return Optional.of(new Detectado("image/webp", "webp"));
    }
    if (pdf(content)) {
      return Optional.of(new Detectado("application/pdf", "pdf"));
    }
    if (isoBaseMedia(content)) {
      String brand = ascii(content, 8, 4).toLowerCase(Locale.ROOT);
      return Optional.of("qt  ".equals(brand)
          ? new Detectado("video/quicktime", "mov")
          : new Detectado("video/mp4", "mp4"));
    }
    return Optional.empty();
  }

  boolean mimeEquivalente(String left, String right) {
    return normalizar(left).equals(normalizar(right));
  }

  private String normalizar(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
  }

  private boolean jpeg(byte[] content) {
    return content.length >= 3
        && unsigned(content[0]) == 0xff
        && unsigned(content[1]) == 0xd8
        && unsigned(content[2]) == 0xff;
  }

  private boolean png(byte[] content) {
    byte[] signature = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    return content.length >= signature.length
        && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
  }

  private boolean webp(byte[] content) {
    return content.length >= 12
        && "RIFF".equals(ascii(content, 0, 4))
        && "WEBP".equals(ascii(content, 8, 4));
  }

  private boolean pdf(byte[] content) {
    return content.length >= 5 && "%PDF-".equals(ascii(content, 0, 5));
  }

  private boolean isoBaseMedia(byte[] content) {
    return content.length >= 12 && "ftyp".equals(ascii(content, 4, 4));
  }

  private String ascii(byte[] content, int offset, int length) {
    if (content.length < offset + length) {
      return "";
    }
    return new String(content, offset, length, StandardCharsets.US_ASCII);
  }

  private int unsigned(byte value) {
    return value & 0xff;
  }

  record Detectado(String mimeType, String extensao) {
  }
}
