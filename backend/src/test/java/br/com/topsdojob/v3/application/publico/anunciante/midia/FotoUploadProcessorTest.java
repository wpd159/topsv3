package br.com.topsdojob.v3.application.publico.anunciante.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoProcessada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor.FotoRestritaDerivada;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.NodeList;

class FotoUploadProcessorTest {

  private static final MidiaUploadProperties PROPERTIES = new MidiaUploadProperties();
  private static final MidiaUploadValidator VALIDATOR = new MidiaUploadValidator(PROPERTIES);
  private static FotoUploadProcessor processor;

  @BeforeAll
  static void setUp() {
    ImageIO.scanForPlugins();
    processor = new FotoUploadProcessor(PROPERTIES);
  }

  @Test
  void reduzJpegGrandeHorizontalEVerticalSemAlterarProporcao() throws Exception {
    FotoProcessada horizontal = processar(jpeg(amostra(3000, 1800, "horizontal"), 1.0f), "foto.jpg");
    FotoProcessada vertical = processar(jpeg(amostra(1800, 3000, "vertical"), 1.0f), "foto.jpg");

    assertThat(horizontal.largura()).isEqualTo(2560);
    assertThat(horizontal.altura()).isEqualTo(1536);
    assertThat(vertical.largura()).isEqualTo(1536);
    assertThat(vertical.altura()).isEqualTo(2560);
    assertThat(horizontal.mimeType()).isEqualTo("image/jpeg");
    assertJpeg444(horizontal.bytes());
  }

  @Test
  void nuncaAmpliaImagemMenorENaoReduzExatamente2560() throws Exception {
    FotoProcessada pequena = processar(jpeg(amostra(640, 480, "pequena"), 0.96f), "foto.jpg");
    FotoProcessada limite = processar(jpeg(amostra(2560, 1440, "limite"), 0.96f), "foto.jpg");

    assertThat(pequena.largura()).isEqualTo(640);
    assertThat(pequena.altura()).isEqualTo(480);
    assertThat(limite.largura()).isEqualTo(2560);
    assertThat(limite.altura()).isEqualTo(1440);
  }

  @Test
  void preservaWebpEQualidadeConfigurada() throws Exception {
    byte[] input = webp(amostra(900, 600, "webp"), 0.98f);
    FotoProcessada output = processar(input, "foto.webp");

    assertThat(output.mimeType()).isEqualTo("image/webp");
    assertThat(output.extensao()).isEqualTo("webp");
    assertThat(ImageIO.read(new ByteArrayInputStream(output.bytes()))).isNotNull();
  }

  @Test
  void mantemPngSomenteQuandoTransparenciaEhRealmenteNecessaria() throws Exception {
    BufferedImage transparent = new BufferedImage(500, 400, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = transparent.createGraphics();
    graphics.setColor(new Color(210, 60, 130, 160));
    graphics.fillOval(30, 30, 420, 320);
    graphics.dispose();

    FotoProcessada comAlpha = processar(png(transparent), "foto.png");
    FotoProcessada opaca = processar(png(amostra(500, 400, "png-opaca")), "foto.png");

    assertThat(comAlpha.mimeType()).isEqualTo("image/png");
    assertThat(ImageIO.read(new ByteArrayInputStream(comAlpha.bytes())).getColorModel().hasAlpha()).isTrue();
    assertThat(opaca.mimeType()).isEqualTo("image/jpeg");
  }

  @Test
  void corrigeOrientacaoExifERemoveExifEGpsDaSaida() throws Exception {
    byte[] base = jpeg(amostra(600, 400, "exif"), 0.96f);
    byte[] input = adicionarExifOrientacaoEGps(base, 6);
    Metadata metadataEntrada = ImageMetadataReader.readMetadata(new ByteArrayInputStream(input));
    assertThat(metadataEntrada.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNotNull();
    assertThat(metadataEntrada.getFirstDirectoryOfType(GpsDirectory.class)).isNotNull();

    FotoProcessada output = processar(input, "foto.jpg");
    Metadata metadataSaida = ImageMetadataReader.readMetadata(new ByteArrayInputStream(output.bytes()));

    assertThat(output.largura()).isEqualTo(400);
    assertThat(output.altura()).isEqualTo(600);
    assertThat(metadataSaida.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
    assertThat(metadataSaida.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    assertThat(new String(output.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS");
  }

  @Test
  void derivacaoRestritaAplicaBlurRealDeterministicoERemoveMetadados() throws Exception {
    BufferedImage source = amostra(1400, 900, "restrita");
    byte[] input = adicionarExifOrientacaoEGps(jpeg(source, 0.98f), 1);

    FotoRestritaDerivada primeira = processor.gerarDerivacaoRestrita(input, "image/jpeg");
    FotoRestritaDerivada segunda = processor.gerarDerivacaoRestrita(input, "image/jpeg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(primeira.bytes()));
    Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(primeira.bytes()));

    assertThat(primeira.largura()).isEqualTo(960);
    assertThat(primeira.altura()).isEqualTo(617);
    assertThat(primeira.mimeType()).isEqualTo("image/jpeg");
    assertThat(primeira.bytes()).containsExactly(segunda.bytes());
    assertThat(primeira.sha256()).isEqualTo(segunda.sha256());
    assertThat(variacaoEntrePixels(decoded))
        .isLessThan(variacaoEntrePixels(source) * 0.35d);
    assertThat(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
    assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    assertThat(new String(primeira.bytes(), StandardCharsets.ISO_8859_1))
        .doesNotContain("Exif")
        .doesNotContain("GPS");
  }

  @Test
  void derivacaoRestritaPreservaOrientacaoExif() throws Exception {
    byte[] input = adicionarExifOrientacaoEGps(
        jpeg(amostra(600, 400, "restrita-orientada"), 0.96f),
        6);

    FotoRestritaDerivada output = processor.gerarDerivacaoRestrita(input, "image/jpeg");

    assertThat(output.largura()).isEqualTo(400);
    assertThat(output.altura()).isEqualTo(600);
  }

  @Test
  void rejeitaCorrompidaEDecompressionBombAntesDoDecodeIntegral() throws Exception {
    byte[] corrupta = jpeg(amostra(100, 80, "corrupta"), 0.9f);
    corrupta = java.util.Arrays.copyOf(corrupta, corrupta.length / 2);
    MockMultipartFile arquivoCorrompido = new MockMultipartFile(
        "arquivo", "foto.jpg", "image/jpeg", corrupta);

    assertThatThrownBy(() -> VALIDATOR.validar(arquivoCorrompido))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

    MidiaUploadProperties restrictive = new MidiaUploadProperties();
    restrictive.setMaxImagePixels(10_000);
    MidiaUploadValidator validator = new MidiaUploadValidator(restrictive);
    MockMultipartFile bomb = new MockMultipartFile(
        "arquivo", "foto.png", "image/png", png(amostra(101, 100, "pixels")));

    assertThatThrownBy(() -> validator.validar(bomb))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
  }

  @Test
  void aplicaMarcaOficialUmaVezERegistraVersoes() throws Exception {
    BufferedImage source = amostra(1000, 700, "marca");
    FotoProcessada output = processar(jpeg(source, 1.0f), "foto.jpg");
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));

    assertThat(output.pipelineVersao()).isEqualTo(FotoUploadProcessor.PIPELINE_VERSION);
    assertThat(output.marcaDaguaVersao()).isEqualTo(FotoUploadProcessor.WATERMARK_VERSION);
    assertThat(output.sha256()).isNotEqualTo(output.sha256Origem());
    assertThat(diferencaMediaCentro(source, decoded)).isGreaterThan(2.0d);
  }

  @Test
  void geraComparacaoVisualDasAmostrasComQualidadeAlta() throws Exception {
    Path directory = Path.of("target", "visual-upload-comparison");
    Files.createDirectories(directory);
    StringBuilder summary = new StringBuilder("amostra\tdimensoes\tantes\tdepois\treducao_pct\tpsnr_fora_marca\n");
    for (String name : List.of("clara", "escura", "pele", "cabelo", "tatuagem", "tecido", "sombras-gradientes")) {
      BufferedImage original = amostraVisual(1200, 800, name);
      byte[] input = jpeg(original, 1.0f);
      FotoProcessada output = processar(input, "foto.jpg");
      BufferedImage sourceDecoded = ImageIO.read(new ByteArrayInputStream(input));
      BufferedImage outputDecoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));
      double psnr = psnrForaMarca(sourceDecoded, outputDecoded);
      double reduction = 100d * (input.length - output.bytes().length) / input.length;
      assertThat(psnr).as(name).isGreaterThan(34d);
      ImageIO.write(ladoALado(sourceDecoded, outputDecoded), "jpg", directory.resolve(name + ".jpg").toFile());
      summary.append(name).append('\t').append(output.largura()).append('x').append(output.altura())
          .append('\t').append(input.length).append('\t').append(output.bytes().length)
          .append('\t').append(String.format(java.util.Locale.ROOT, "%.2f", reduction))
          .append('\t').append(String.format(java.util.Locale.ROOT, "%.2f", psnr)).append('\n');
    }
    Files.writeString(directory.resolve("summary.tsv"), summary);
  }

  private static FotoProcessada processar(byte[] bytes, String filename) {
    String mime = filename.endsWith(".png") ? "image/png" : filename.endsWith(".webp") ? "image/webp" : "image/jpeg";
    MidiaValidada upload = VALIDATOR.validar(new MockMultipartFile("arquivo", filename, mime, bytes));
    return processor.processar(upload);
  }

  private static BufferedImage amostra(int width, int height, String label) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setPaint(new GradientPaint(0, 0, new Color(245, 210, 200), width, height, new Color(35, 28, 48)));
    graphics.fillRect(0, 0, width, height);
    graphics.setColor(new Color(255, 255, 255, 180));
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(16, width / 18)));
    graphics.drawString(label, width / 12, height / 2);
    for (int x = 0; x < width; x += 13) {
      graphics.setColor(new Color((x * 17) & 255, (x * 29) & 255, (x * 41) & 255));
      graphics.drawLine(x, 0, width - x / 2, height - 1);
    }
    graphics.dispose();
    return image;
  }

  private static BufferedImage amostraVisual(int width, int height, String name) {
    BufferedImage image = amostra(width, height, name);
    Graphics2D graphics = image.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    if (name.equals("escura")) {
      graphics.setColor(new Color(8, 8, 12, 190));
      graphics.fillRect(0, 0, width, height);
    } else if (name.equals("pele")) {
      graphics.setPaint(new GradientPaint(0, 0, new Color(92, 52, 38), width, height, new Color(249, 205, 174)));
      graphics.fillRect(0, 0, width, height);
    } else if (name.equals("cabelo")) {
      graphics.setColor(new Color(25, 15, 12));
      graphics.fillRect(0, 0, width, height);
      for (int x = 0; x < width; x += 3) {
        graphics.setColor(new Color(40 + x % 80, 25 + x % 45, 20 + x % 25));
        graphics.drawArc(x - 100, -100, 300, height + 200, 70, 80);
      }
    } else if (name.equals("tatuagem")) {
      graphics.setColor(new Color(224, 173, 143));
      graphics.fillRect(0, 0, width, height);
      graphics.setColor(new Color(20, 25, 30));
      for (int radius = 40; radius < 500; radius += 24) graphics.drawOval(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2);
    } else if (name.equals("tecido")) {
      for (int y = 0; y < height; y += 8) {
        for (int x = 0; x < width; x += 8) {
          graphics.setColor(((x + y) / 8) % 2 == 0 ? new Color(120, 25, 60) : new Color(245, 190, 205));
          graphics.fillRect(x, y, 8, 8);
        }
      }
    } else if (name.equals("sombras-gradientes")) {
      graphics.setPaint(new GradientPaint(0, 0, Color.BLACK, width, 0, Color.WHITE));
      graphics.fillRect(0, 0, width, height);
    }
    graphics.dispose();
    return image;
  }

  private static byte[] jpeg(BufferedImage image, float quality) throws Exception {
    ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/jpeg").next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), param);
      output.flush();
      return bytes.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  private static byte[] png(BufferedImage image) throws Exception {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", bytes);
      return bytes.toByteArray();
    }
  }

  private static byte[] webp(BufferedImage image, float quality) throws Exception {
    ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
      param.setCompressionType(CompressionType.Lossy);
      param.setCompressionQuality(quality);
      param.setUseSharpYUV(true);
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), param);
      output.flush();
      return bytes.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  private static byte[] adicionarExifOrientacaoEGps(byte[] jpeg, int orientation) throws Exception {
    ByteBuffer tiff = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    tiff.putShort((short) 2);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
    tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
    tiff.putInt(0);
    tiff.putShort((short) 1);
    tiff.putShort((short) 1).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
    tiff.putInt(0);
    byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
    int payloadLength = exif.length + tiff.position();
    ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + payloadLength + 4);
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

  private static void assertJpeg444(byte[] bytes) throws Exception {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType("image/jpeg");
    ImageReader reader = readers.next();
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      reader.setInput(input);
      IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(0)
          .getAsTree("javax_imageio_jpeg_image_1.0");
      NodeList components = root.getElementsByTagName("componentSpec");
      assertThat(components.getLength()).isEqualTo(3);
      for (int index = 0; index < components.getLength(); index++) {
        IIOMetadataNode component = (IIOMetadataNode) components.item(index);
        assertThat(component.getAttribute("HsamplingFactor")).isEqualTo("1");
        assertThat(component.getAttribute("VsamplingFactor")).isEqualTo("1");
      }
    } finally {
      reader.dispose();
    }
  }

  private static double diferencaMediaCentro(BufferedImage original, BufferedImage output) {
    int x0 = original.getWidth() / 3;
    int x1 = original.getWidth() * 2 / 3;
    int y0 = original.getHeight() * 2 / 5;
    int y1 = original.getHeight() * 3 / 5;
    long difference = 0;
    long count = 0;
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) {
        int a = original.getRGB(x, y);
        int b = output.getRGB(x, y);
        difference += Math.abs(((a >> 16) & 255) - ((b >> 16) & 255));
        difference += Math.abs(((a >> 8) & 255) - ((b >> 8) & 255));
        difference += Math.abs((a & 255) - (b & 255));
        count += 3;
      }
    }
    return difference / (double) count;
  }

  private static double variacaoEntrePixels(BufferedImage image) {
    long total = 0;
    long samples = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 1; x < image.getWidth(); x++) {
        int left = image.getRGB(x - 1, y);
        int right = image.getRGB(x, y);
        for (int shift : new int[] {16, 8, 0}) {
          total += Math.abs(((left >> shift) & 255) - ((right >> shift) & 255));
          samples++;
        }
      }
    }
    return total / (double) samples;
  }

  private static double psnrForaMarca(BufferedImage source, BufferedImage output) {
    double scale = Math.min(source.getWidth() * 0.55d / 296d, source.getHeight() * 0.55d / 80d);
    int wmWidth = (int) Math.round(296 * scale);
    int wmHeight = (int) Math.round(80 * scale);
    int x0 = (source.getWidth() - wmWidth) / 2;
    int y0 = (source.getHeight() - wmHeight) / 2;
    long samples = 0;
    double squared = 0;
    for (int y = 0; y < source.getHeight(); y++) {
      for (int x = 0; x < source.getWidth(); x++) {
        if (x >= x0 && x < x0 + wmWidth && y >= y0 && y < y0 + wmHeight) continue;
        int a = source.getRGB(x, y);
        int b = output.getRGB(x, y);
        for (int shift : new int[] {16, 8, 0}) {
          int delta = ((a >> shift) & 255) - ((b >> shift) & 255);
          squared += delta * delta;
          samples++;
        }
      }
    }
    double mse = squared / samples;
    return mse == 0 ? 99d : 10d * Math.log10(255d * 255d / mse);
  }

  private static BufferedImage ladoALado(BufferedImage source, BufferedImage output) {
    BufferedImage comparison = new BufferedImage(source.getWidth() * 2, source.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = comparison.createGraphics();
    graphics.drawImage(source, 0, 0, null);
    graphics.drawImage(output, source.getWidth(), 0, null);
    graphics.dispose();
    return comparison;
  }
}
