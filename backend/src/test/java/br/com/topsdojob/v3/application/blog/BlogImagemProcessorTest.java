package br.com.topsdojob.v3.application.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BlogImagemProcessorTest {

  @Test
  void limitaMaiorLadoERecodificaSemMetadadosDeOrigem() throws Exception {
    BufferedImage source = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = source.createGraphics();
    graphics.setColor(Color.MAGENTA);
    graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
    graphics.dispose();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(source, "jpg", output);
    byte[] original = output.toByteArray();

    MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    MockMultipartFile file = new MockMultipartFile(
        "imagem", "capa.jpg", "image/jpeg", original);
    when(validator.validar(file)).thenReturn(new MidiaValidada(
        original, false, "image/jpeg", "jpg", "capa.jpg", 3000, 1000, null, "origem"));

    var result = new BlogImagemProcessor(validator).processar(file, "CAPA");

    assertThat(result.largura()).isEqualTo(2400);
    assertThat(result.altura()).isEqualTo(800);
    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    assertThat(result.extensao()).isEqualTo("jpg");
    assertThat(result.sha256()).matches("[0-9a-f]{64}");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()))).isNotNull();
  }

  @Test
  void reduzBytesDoJpegOpacoSemAlterarDimensoesOuRecorte() throws Exception {
    BufferedImage source = new BufferedImage(1200, 630, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        int red = (x * 7 + y * 13) & 255;
        int green = (x ^ y) & 255;
        int blue = (x * 3 + y * 5) & 255;
        source.setRGB(x, y, (red << 16) | (green << 8) | blue);
      }
    }
    ByteArrayOutputStream upload = new ByteArrayOutputStream();
    ImageIO.write(source, "png", upload);
    byte[] original = upload.toByteArray();
    MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    MockMultipartFile file = new MockMultipartFile("imagem", "capa.png", "image/png", original);
    when(validator.validar(file)).thenReturn(new MidiaValidada(
        original, false, "image/png", "png", "capa.png", 1200, 630, null, "origem"));

    var processor = new BlogImagemProcessor(validator);
    var result = processor.processar(file, "CAPA");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    assertThat(result.extensao()).isEqualTo("jpg");
    assertThat(result.largura()).isEqualTo(1200);
    assertThat(result.altura()).isEqualTo(630);
    assertThat(result.bytes().length).isLessThan(jpegBytes(source, 0.9f).length);
    assertThat(result.bytes()).isEqualTo(jpegBytes(source, 0.8f));
    assertThat(processor.processar(file, "OG").bytes()).isEqualTo(jpegBytes(source, 0.9f));
    BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
    assertThat(decoded.getWidth()).isEqualTo(1200);
    assertThat(decoded.getHeight()).isEqualTo(630);
  }

  @Test
  void preservaPngComTransparencia() throws Exception {
    BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    source.setRGB(10, 10, 0x8000ff00);
    ByteArrayOutputStream upload = new ByteArrayOutputStream();
    ImageIO.write(source, "png", upload);
    byte[] original = upload.toByteArray();
    MidiaUploadValidator validator = mock(MidiaUploadValidator.class);
    MockMultipartFile file = new MockMultipartFile("imagem", "capa.png", "image/png", original);
    when(validator.validar(file)).thenReturn(new MidiaValidada(
        original, false, "image/png", "png", "capa.png", 64, 32, null, "origem"));

    var result = new BlogImagemProcessor(validator).processar(file, "CAPA");

    assertThat(result.mimeType()).isEqualTo("image/png");
    assertThat(result.extensao()).isEqualTo("png");
    BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
    assertThat(decoded.getWidth()).isEqualTo(64);
    assertThat(decoded.getHeight()).isEqualTo(32);
    assertThat(decoded.getRGB(10, 10) >>> 24).isEqualTo(0x80);
  }

  private static byte[] jpegBytes(BufferedImage source, float quality) throws Exception {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.write(null, new IIOImage(source, null, null), param);
      return output.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
