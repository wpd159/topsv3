package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadProperties;
import br.com.topsdojob.v3.application.publico.kyc.DocumentoUploadValidator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class LegacyKycDocumentValidator {

  private final DocumentoUploadProperties properties = new DocumentoUploadProperties();
  private final DocumentoUploadValidator canonical = new DocumentoUploadValidator(properties);

  DocumentoUploadValidator.DocumentoValidado validar(byte[] bytes, String extension) {
    if (bytes.length > properties.getMaxBytes()) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "documento acima do limite permitido");
    }
    if (!"jpg".equals(extension) || !hasJpegEnvelope(bytes)) {
      return canonical.validar(new MockMultipartFile(
          "arquivo", "origem." + extension, "application/octet-stream", bytes));
    }

    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
        throw invalidImage();
      }
      return new DocumentoUploadValidator.DocumentoValidado(
          bytes,
          "image/jpeg",
          "jpg",
          image.getWidth(),
          image.getHeight(),
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalidImage();
    }
  }

  private boolean hasJpegEnvelope(byte[] bytes) {
    if (bytes.length < 4 || (bytes[0] & 0xff) != 0xff || (bytes[1] & 0xff) != 0xd8) {
      return false;
    }
    for (int index = bytes.length - 2; index >= 2; index--) {
      if ((bytes[index] & 0xff) == 0xff && (bytes[index + 1] & 0xff) == 0xd9) {
        return true;
      }
    }
    return false;
  }

  private ResponseStatusException invalidImage() {
    return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "documento legado invalido");
  }
}
