package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LegacyKycDocumentValidatorTest {

  private final LegacyKycDocumentValidator validator = new LegacyKycDocumentValidator();

  @Test
  void aceitaJpegLegadoDecodificavelComMetadadosDepoisDoEoi() throws Exception {
    byte[] jpeg = jpeg();
    byte[] legacy = Arrays.copyOf(jpeg, jpeg.length + 6 * 1024 * 1024);

    var validated = validator.validar(legacy, "jpg");

    assertThat(validated.mimeType()).isEqualTo("image/jpeg");
    assertThat(validated.largura()).isEqualTo(3);
    assertThat(validated.altura()).isEqualTo(2);
    assertThat(validated.bytes()).isEqualTo(legacy);
  }

  @Test
  void preservaFormatosCanonicosERecusaHeicOuJpegCorrompido() throws Exception {
    assertThat(validator.validar(png(), "png").mimeType()).isEqualTo("image/png");

    assertUnsupported(new byte[] {
        0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63
    }, "heic");
    assertUnsupported(new byte[] {(byte) 0xff, (byte) 0xd8, 0x00, (byte) 0xff, (byte) 0xd9}, "jpg");
  }

  private void assertUnsupported(byte[] bytes, String extension) {
    assertThatThrownBy(() -> validator.validar(bytes, extension))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
  }

  private byte[] jpeg() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    return output.toByteArray();
  }

  private byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
