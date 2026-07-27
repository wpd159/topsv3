package br.com.topsdojob.v3.application.admin.documento;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;

class AdminKycThumbnailProcessorTest {

  private final AdminKycThumbnailProcessor processor = new AdminKycThumbnailProcessor();

  @Test
  void otimizaJpegEPngSemCarregarNovamenteOArquivoIntegral() throws Exception {
    for (String format : new String[] {"jpeg", "png"}) {
      byte[] source = image(format, 1600, 900);
      AtomicInteger reads = new AtomicInteger();
      UUID fileId = UUID.randomUUID();

      var first = processor.processar(fileId, "sha-" + format, "image/" + format, () -> {
        reads.incrementAndGet();
        return source;
      });
      var cached = processor.processar(fileId, "sha-" + format, "image/" + format, () -> {
        reads.incrementAndGet();
        return source;
      });

      BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(first.content()));
      assertThat(first.contentType()).isEqualTo("image/jpeg");
      assertThat(thumbnail.getWidth()).isEqualTo(480);
      assertThat(thumbnail.getHeight()).isEqualTo(270);
      assertThat(cached.content()).containsExactly(first.content());
      assertThat(reads).hasValue(1);
    }
  }

  @Test
  void renderizaSomenteAPrimeiraPaginaDoPdf() throws Exception {
    byte[] source;
    try (PDDocument document = new PDDocument()) {
      PDPage first = new PDPage();
      document.addPage(first);
      document.addPage(new PDPage());
      try (PDPageContentStream content = new PDPageContentStream(document, first)) {
        content.setNonStrokingColor(Color.MAGENTA);
        content.addRect(10, 10, 200, 100);
        content.fill();
      }
      try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        document.save(output);
        source = output.toByteArray();
      }
    }

    var result = processor.processar(
        UUID.randomUUID(),
        "pdf-sha",
        "application/pdf",
        () -> source);

    BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.content()));
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isLessThanOrEqualTo(480);
    assertThat(thumbnail.getWidth()).isPositive();
    assertThat(thumbnail.getHeight()).isPositive();
  }

  private byte[] image(String format, int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setPaint(new java.awt.GradientPaint(0, 0, Color.PINK, width, height, Color.BLUE));
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, format, output);
      return output.toByteArray();
    }
  }
}
