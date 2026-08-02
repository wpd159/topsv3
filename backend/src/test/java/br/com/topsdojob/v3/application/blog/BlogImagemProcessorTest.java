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
import javax.imageio.ImageIO;
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

    var result = new BlogImagemProcessor(validator).processar(file);

    assertThat(result.largura()).isEqualTo(2400);
    assertThat(result.altura()).isEqualTo(800);
    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    assertThat(result.extensao()).isEqualTo("jpg");
    assertThat(result.sha256()).matches("[0-9a-f]{64}");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()))).isNotNull();
  }
}
