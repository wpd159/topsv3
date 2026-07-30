package br.com.topsdojob.v3.application.publico.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DocumentoUploadValidatorTest {

  @Test
  void aceitaPngDecodificavelEPdfComEstruturaMinima() throws Exception {
    DocumentoUploadValidator validator = new DocumentoUploadValidator(new DocumentoUploadProperties());

    var image = validator.validar(new MockMultipartFile(
        "documento", "identidade.png", "application/octet-stream", png()));
    var pdf = validator.validar(new MockMultipartFile(
        "documento", "identidade.pdf", "application/octet-stream", pdf()));

    assertThat(image.mimeType()).isEqualTo("image/png");
    assertThat(image.largura()).isEqualTo(3);
    assertThat(image.altura()).isEqualTo(2);
    assertThat(image.sha256()).hasSize(64);
    assertThat(pdf.mimeType()).isEqualTo("application/pdf");
    assertThat(pdf.largura()).isNull();
    assertThat(pdf.altura()).isNull();
  }

  @Test
  void recusaSvgExtensaoIncompativelEArquivoAcimaDoLimite() throws Exception {
    DocumentoUploadProperties properties = new DocumentoUploadProperties();
    properties.setMaxBytes(10);
    DocumentoUploadValidator validator = new DocumentoUploadValidator(properties);

    assertStatus(validator, new MockMultipartFile(
        "documento", "identidade.svg", "image/svg+xml", "<svg/>".getBytes()),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertStatus(validator, new MockMultipartFile(
        "documento", "identidade.pdf", "application/pdf", png()),
        HttpStatus.PAYLOAD_TOO_LARGE);

    DocumentoUploadValidator defaultValidator = new DocumentoUploadValidator(new DocumentoUploadProperties());
    assertStatus(defaultValidator, new MockMultipartFile(
        "documento", "identidade.pdf", "application/pdf", png()),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void validadoresEspecificosRecusamFormatoFalsoComMensagemHumana() throws Exception {
    DocumentoUploadValidator validator =
        new DocumentoUploadValidator(new DocumentoUploadProperties());

    assertReason(
        () -> validator.validarPdf(new MockMultipartFile(
            "documentoUnico", "identidade.png", "image/png", png())),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "O arquivo deve estar em PDF.");
    assertReason(
        () -> validator.validarPdf(new MockMultipartFile(
            "documentoUnico", "identidade.pdf", "application/pdf", "corrompido".getBytes())),
        HttpStatus.BAD_REQUEST,
        "Não foi possível ler o PDF enviado.");
    assertReason(
        () -> validator.validarImagem(new MockMultipartFile(
            "documentoFrente", "identidade.webp", "image/webp", png())),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "A imagem deve estar em JPG ou PNG.");
    assertReason(
        () -> validator.validarImagem(new MockMultipartFile(
            "documentoFrente", "identidade.png", "image/png", "corrompido".getBytes())),
        HttpStatus.BAD_REQUEST,
        "Não foi possível ler a imagem enviada.");
  }

  private void assertReason(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable operation,
      HttpStatus expected,
      String reason) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
          assertThat(exception.getStatusCode()).isEqualTo(expected);
          assertThat(exception.getReason()).isEqualTo(reason);
        });
  }

  private void assertStatus(
      DocumentoUploadValidator validator,
      MockMultipartFile file,
      HttpStatus expected) {
    assertThatThrownBy(() -> validator.validar(file))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(expected));
  }

  private byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private byte[] pdf() {
    return "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes();
  }
}
