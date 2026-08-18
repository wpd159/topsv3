package br.com.topsdojob.v3.application.admin.documento;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
  void respeitaTodasAsOrientacoesExifAntesDeGerarAMiniatura(int orientation) throws Exception {
    byte[] source = adicionarExifOrientacao(imageWithQuadrants(), orientation);

    var result = processor.processar(
        UUID.randomUUID(),
        "exif-" + orientation,
        "image/jpeg",
        () -> source);

    BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(result.content()));
    boolean swapsDimensions = orientation >= 5;
    assertThat(thumbnail.getWidth()).isEqualTo(swapsDimensions ? 320 : 480);
    assertThat(thumbnail.getHeight()).isEqualTo(swapsDimensions ? 480 : 320);

    Color[] expectedCorners = expectedCorners(orientation);
    assertColor(thumbnail, 0.2d, 0.2d, expectedCorners[0]);
    assertColor(thumbnail, 0.8d, 0.2d, expectedCorners[1]);
    assertColor(thumbnail, 0.2d, 0.8d, expectedCorners[2]);
    assertColor(thumbnail, 0.8d, 0.8d, expectedCorners[3]);
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

  private byte[] imageWithQuadrants() throws Exception {
    int width = 600;
    int height = 400;
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.RED);
      graphics.fillRect(0, 0, width / 2, height / 2);
      graphics.setColor(Color.GREEN);
      graphics.fillRect(width / 2, 0, width / 2, height / 2);
      graphics.setColor(Color.BLUE);
      graphics.fillRect(0, height / 2, width / 2, height / 2);
      graphics.setColor(Color.YELLOW);
      graphics.fillRect(width / 2, height / 2, width / 2, height / 2);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpeg", output);
      return output.toByteArray();
    }
  }

  private byte[] adicionarExifOrientacao(byte[] jpeg, int orientation) throws Exception {
    ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    tiff.putShort((short) 1);
    tiff.putShort((short) 0x0112)
        .putShort((short) 3)
        .putInt(1)
        .putShort((short) orientation)
        .putShort((short) 0);
    tiff.putInt(0);

    byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    int payloadLength = exif.length + tiff.position();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + payloadLength + 4)) {
      output.write(jpeg, 0, 2);
      output.write(0xff);
      output.write(0xe1);
      output.write((payloadLength + 2) >>> 8);
      output.write((payloadLength + 2) & 0xff);
      output.write(exif);
      output.write(tiff.array(), 0, tiff.position());
      output.write(jpeg, 2, jpeg.length - 2);
      return output.toByteArray();
    }
  }

  private Color[] expectedCorners(int orientation) {
    return switch (orientation) {
      case 2 -> new Color[] {Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE};
      case 3 -> new Color[] {Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED};
      case 4 -> new Color[] {Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN};
      case 5 -> new Color[] {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};
      case 6 -> new Color[] {Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN};
      case 7 -> new Color[] {Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED};
      case 8 -> new Color[] {Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE};
      default -> new Color[] {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
    };
  }

  private void assertColor(BufferedImage image, double xRatio, double yRatio, Color expected) {
    int x = Math.min(image.getWidth() - 1, (int) Math.round(image.getWidth() * xRatio));
    int y = Math.min(image.getHeight() - 1, (int) Math.round(image.getHeight() * yRatio));
    Color actual = new Color(image.getRGB(x, y));
    assertThat(Math.abs(actual.getRed() - expected.getRed())).isLessThanOrEqualTo(45);
    assertThat(Math.abs(actual.getGreen() - expected.getGreen())).isLessThanOrEqualTo(45);
    assertThat(Math.abs(actual.getBlue() - expected.getBlue())).isLessThanOrEqualTo(45);
  }
}
