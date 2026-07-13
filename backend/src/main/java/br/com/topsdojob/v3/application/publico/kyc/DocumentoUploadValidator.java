package br.com.topsdojob.v3.application.publico.kyc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentoUploadValidator {

  private final DocumentoUploadProperties properties;

  public DocumentoUploadValidator(DocumentoUploadProperties properties) {
    this.properties = properties;
  }

  public DocumentoValidado validar(MultipartFile multipart) {
    if (multipart == null || multipart.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documento obrigatorio");
    }
    if (multipart.getSize() > properties.getMaxBytes()) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "documento acima do limite permitido");
    }
    byte[] bytes = bytes(multipart);
    String extensao = extensao(multipart.getOriginalFilename());
    Tipo tipo = detectar(bytes, extensao);
    Dimensoes dimensoes = tipo.pdf() ? new Dimensoes(null, null) : dimensoesImagem(bytes);
    return new DocumentoValidado(
        bytes,
        tipo.mimeType(),
        tipo.extensao(),
        dimensoes.largura(),
        dimensoes.altura(),
        sha256(bytes));
  }

  private byte[] bytes(MultipartFile multipart) {
    try {
      return multipart.getBytes();
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documento nao pode ser lido");
    }
  }

  private Tipo detectar(byte[] bytes, String extensao) {
    if (jpeg(bytes) && ("jpg".equals(extensao) || "jpeg".equals(extensao))) {
      return new Tipo(false, "image/jpeg", "jpg");
    }
    if (png(bytes) && "png".equals(extensao)) {
      return new Tipo(false, "image/png", "png");
    }
    if (pdf(bytes) && "pdf".equals(extensao)) {
      return new Tipo(true, "application/pdf", "pdf");
    }
    throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "formato de documento nao permitido");
  }

  private Dimensoes dimensoesImagem(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
        throw formatoInvalido();
      }
      return new Dimensoes(image.getWidth(), image.getHeight());
    } catch (IOException exception) {
      throw formatoInvalido();
    }
  }

  private boolean jpeg(byte[] bytes) {
    return bytes.length >= 4
        && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8
        && (bytes[bytes.length - 2] & 0xff) == 0xff
        && (bytes[bytes.length - 1] & 0xff) == 0xd9;
  }

  private boolean png(byte[] bytes) {
    byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    if (bytes.length < signature.length) return false;
    for (int index = 0; index < signature.length; index++) {
      if (bytes[index] != signature[index]) return false;
    }
    return true;
  }

  private boolean pdf(byte[] bytes) {
    if (bytes.length < 9) return false;
    String header = new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
    int start = Math.max(0, bytes.length - 2048);
    String tail = new String(
        bytes,
        start,
        bytes.length - start,
        java.nio.charset.StandardCharsets.ISO_8859_1);
    return "%PDF-".equals(header) && tail.contains("%%EOF");
  }

  private String extensao(String filename) {
    String value = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
    int dot = value.lastIndexOf('.');
    return dot < 0 || dot == value.length() - 1 ? "" : value.substring(dot + 1);
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private ResponseStatusException formatoInvalido() {
    return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "documento de imagem invalido");
  }

  public record DocumentoValidado(
      byte[] bytes,
      String mimeType,
      String extensao,
      Integer largura,
      Integer altura,
      String sha256) {
  }

  private record Tipo(boolean pdf, String mimeType, String extensao) {
  }

  private record Dimensoes(Integer largura, Integer altura) {
  }
}
