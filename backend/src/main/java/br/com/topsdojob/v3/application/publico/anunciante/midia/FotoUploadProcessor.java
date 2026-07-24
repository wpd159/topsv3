package br.com.topsdojob.v3.application.publico.anunciante.midia;

import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.NodeList;

@Component
public class FotoUploadProcessor {

  public static final int PIPELINE_VERSION = 1;
  public static final String WATERMARK_VERSION =
      "810665d22b868cd625577261b2c017ee808d7d979d0aff0d86891a589d46b851";

  private static final String WATERMARK_RESOURCE = "marca-dagua.webp";
  private static final int WATERMARK_WIDTH = 296;
  private static final int WATERMARK_HEIGHT = 80;
  private static final double WATERMARK_SCALE = 0.55d;
  private static final float WATERMARK_OPACITY = 0.12f;
  private static final String JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0";
  private static final int RESTRICTED_PREVIEW_MAX_SIDE = 960;
  private static final int RESTRICTED_PREVIEW_BLUR_SIDE = 48;
  private static final int RESTRICTED_PREVIEW_KERNEL_SIDE = 5;

  private final MidiaUploadProperties properties;
  private final BufferedImage watermark;

  public FotoUploadProcessor(MidiaUploadProperties properties) {
    this.properties = properties;
    validarConfiguracao(properties);
    ImageIO.scanForPlugins();
    this.watermark = carregarMarcaDagua();
  }

  public FotoProcessada processar(MidiaValidada upload) {
    if (upload == null || upload.video()) {
      throw new IllegalArgumentException("O pipeline de foto nao aceita video");
    }
    BufferedImage decoded = decodificar(upload.bytes(), upload.mimeType());
    BufferedImage oriented = orientar(decoded, orientacaoExif(upload.bytes()));
    BufferedImage srgb = converterSrgb(oriented);
    BufferedImage resized = reduzirSeNecessario(srgb);
    boolean transparenciaNecessaria = possuiTransparencia(resized);
    BufferedImage marked = aplicarMarcaDagua(resized, transparenciaNecessaria);
    FormatoSaida formato = formatoSaida(upload.mimeType(), transparenciaNecessaria);
    byte[] bytes = codificar(marked, formato);
    validarDerivado(bytes, formato.mimeType(), marked.getWidth(), marked.getHeight());
    return new FotoProcessada(
        bytes,
        formato.mimeType(),
        formato.extensao(),
        marked.getWidth(),
        marked.getHeight(),
        sha256(bytes),
        upload.sha256(),
        PIPELINE_VERSION,
        WATERMARK_VERSION,
        OffsetDateTime.now(ZoneOffset.UTC));
  }

  public FotoRestritaDerivada gerarDerivacaoRestrita(byte[] bytes, String mimeType) {
    if (bytes == null || bytes.length == 0 || mimeType == null || !mimeType.startsWith("image/")) {
      throw new IllegalArgumentException("Imagem restrita obrigatoria");
    }
    String mimeNormalizado = mimeType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
    BufferedImage decoded = decodificar(bytes, mimeNormalizado);
    BufferedImage oriented = orientar(decoded, orientacaoExif(bytes));
    BufferedImage srgb = converterSrgb(oriented);
    BufferedImage preview = reduzirAte(srgb, RESTRICTED_PREVIEW_MAX_SIDE);
    BufferedImage blurred = aplicarDesfoqueIrreversivel(preview);
    byte[] derivado = codificarJpeg(blurred);
    validarDerivado(derivado, "image/jpeg", blurred.getWidth(), blurred.getHeight());
    return new FotoRestritaDerivada(
        derivado,
        "image/jpeg",
        "jpg",
        blurred.getWidth(),
        blurred.getHeight(),
        sha256(derivado));
  }

  public void validarDerivado(byte[] bytes, String mimeType, int largura, int altura) {
    if (!assinaturaCompativel(bytes, mimeType)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "arquivo processado possui assinatura invalida");
    }
    BufferedImage decoded = decodificar(bytes, mimeType);
    if (decoded.getWidth() != largura || decoded.getHeight() != altura) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "arquivo processado possui dimensoes divergentes");
    }
  }

  private BufferedImage decodificar(byte[] bytes, String mimeType) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(mimeType);
    if (!readers.hasNext()) {
      throw formatoInvalido();
    }
    ImageReader reader = readers.next();
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) throw formatoInvalido();
      reader.setInput(input, true, true);
      int width = reader.getWidth(0);
      int height = reader.getHeight(0);
      validarDimensoes(width, height);
      BufferedImage image = reader.read(0, reader.getDefaultReadParam());
      if (image == null || image.getWidth() != width || image.getHeight() != height) {
        throw formatoInvalido();
      }
      return image;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw formatoInvalido();
    } finally {
      reader.dispose();
    }
  }

  private void validarDimensoes(int width, int height) {
    long pixels = (long) width * height;
    if (width < 1 || height < 1
        || width > properties.getMaxImageDimension()
        || height > properties.getMaxImageDimension()
        || pixels > properties.getMaxImagePixels()) {
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "imagem excede o limite seguro de pixels");
    }
  }

  private int orientacaoExif(byte[] bytes) {
    try (InputStream input = new ByteArrayInputStream(bytes)) {
      Metadata metadata = ImageMetadataReader.readMetadata(input);
      ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) return 1;
      int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
      return orientation >= 1 && orientation <= 8 ? orientation : 1;
    } catch (Exception ignored) {
      return 1;
    }
  }

  private BufferedImage orientar(BufferedImage source, int orientation) {
    if (orientation == 1) return source;
    int width = source.getWidth();
    int height = source.getHeight();
    boolean swap = orientation >= 5;
    BufferedImage target = imagemVazia(swap ? height : width, swap ? width : height, source.getColorModel().hasAlpha());
    AffineTransform transform = switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
      default -> new AffineTransform();
    };
    Graphics2D graphics = target.createGraphics();
    try {
      configurarQualidade(graphics);
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private BufferedImage converterSrgb(BufferedImage source) {
    boolean alpha = source.getColorModel().hasAlpha();
    BufferedImage target = imagemVazia(source.getWidth(), source.getHeight(), alpha);
    Graphics2D graphics = target.createGraphics();
    try {
      configurarQualidade(graphics);
      graphics.setComposite(AlphaComposite.Src);
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    if (!target.getColorModel().getColorSpace().isCS_sRGB()
        || target.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_RGB) {
      throw new IllegalStateException("Conversao sRGB nao foi concluida");
    }
    return target;
  }

  private BufferedImage reduzirSeNecessario(BufferedImage source) {
    return reduzirAte(source, properties.getMaxProcessedSide());
  }

  private BufferedImage reduzirAte(BufferedImage source, int limiteMaiorLado) {
    int maxSide = Math.max(source.getWidth(), source.getHeight());
    if (maxSide <= limiteMaiorLado) return source;
    double ratio = limiteMaiorLado / (double) maxSide;
    int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
    int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));
    BufferedImage current = source;
    while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
      current = redimensionar(current,
          Math.max(targetWidth, current.getWidth() / 2),
          Math.max(targetHeight, current.getHeight() / 2));
    }
    return current.getWidth() == targetWidth && current.getHeight() == targetHeight
        ? current
        : redimensionar(current, targetWidth, targetHeight);
  }

  private BufferedImage aplicarDesfoqueIrreversivel(BufferedImage source) {
    int maxSide = Math.max(source.getWidth(), source.getHeight());
    double ratio = Math.min(1d, RESTRICTED_PREVIEW_BLUR_SIDE / (double) maxSide);
    int reducedWidth = Math.max(1, (int) Math.round(source.getWidth() * ratio));
    int reducedHeight = Math.max(1, (int) Math.round(source.getHeight() * ratio));
    BufferedImage reduced = redimensionar(source, reducedWidth, reducedHeight);

    BufferedImage convolved = reduced;
    if (reducedWidth >= RESTRICTED_PREVIEW_KERNEL_SIDE
        && reducedHeight >= RESTRICTED_PREVIEW_KERNEL_SIDE) {
      int samples = RESTRICTED_PREVIEW_KERNEL_SIDE * RESTRICTED_PREVIEW_KERNEL_SIDE;
      float[] weights = new float[samples];
      java.util.Arrays.fill(weights, 1f / samples);
      ConvolveOp blur = new ConvolveOp(
          new Kernel(RESTRICTED_PREVIEW_KERNEL_SIDE, RESTRICTED_PREVIEW_KERNEL_SIDE, weights),
          ConvolveOp.EDGE_NO_OP,
          null);
      convolved = blur.filter(reduced, null);
    }

    BufferedImage opaque = convolved.getColorModel().hasAlpha()
        ? converterOpaca(convolved)
        : convolved;
    return redimensionar(opaque, source.getWidth(), source.getHeight());
  }

  private BufferedImage redimensionar(BufferedImage source, int width, int height) {
    BufferedImage target = imagemVazia(width, height, source.getColorModel().hasAlpha());
    Graphics2D graphics = target.createGraphics();
    try {
      configurarQualidade(graphics);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private BufferedImage aplicarMarcaDagua(BufferedImage source, boolean alpha) {
    BufferedImage target = imagemVazia(source.getWidth(), source.getHeight(), alpha);
    Graphics2D graphics = target.createGraphics();
    try {
      configurarQualidade(graphics);
      graphics.drawImage(source, 0, 0, null);
      double scale = Math.min(
          source.getWidth() * WATERMARK_SCALE / watermark.getWidth(),
          source.getHeight() * WATERMARK_SCALE / watermark.getHeight());
      int width = Math.max(1, (int) Math.round(watermark.getWidth() * scale));
      int height = Math.max(1, (int) Math.round(watermark.getHeight() * scale));
      int x = (source.getWidth() - width) / 2;
      int y = (source.getHeight() - height) / 2;
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_OPACITY));
      graphics.drawImage(watermark, x, y, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private byte[] codificar(BufferedImage image, FormatoSaida formato) {
    return switch (formato) {
      case JPEG -> codificarJpeg(image);
      case PNG -> codificarPadrao(image, "png");
      case WEBP -> codificarWebp(image);
    };
  }

  private byte[] codificarJpeg(BufferedImage source) {
    BufferedImage image = source.getType() == BufferedImage.TYPE_INT_RGB
        ? source
        : converterOpaca(source);
    ImageWriter writer = primeiroWriter("image/jpeg");
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(properties.getJpegQuality());
      param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
      IIOMetadata metadata = writer.getDefaultImageMetadata(
          ImageTypeSpecifier.createFromRenderedImage(image), param);
      configurarJpeg444(metadata);
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, metadata), param);
      output.flush();
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw falhaProcessamento(exception);
    } finally {
      writer.dispose();
    }
  }

  private byte[] codificarWebp(BufferedImage image) {
    ImageWriter writer = primeiroWriter("image/webp");
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      WebPWriteParam param = (WebPWriteParam) writer.getDefaultWriteParam();
      param.setCompressionType(CompressionType.Lossy);
      param.setCompressionQuality(properties.getWebpQuality());
      param.setMethod(6);
      param.setAlphaQuality(100);
      param.setUseSharpYUV(true);
      param.setThreadLevel(1);
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), param);
      output.flush();
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw falhaProcessamento(exception);
    } finally {
      writer.dispose();
    }
  }

  private byte[] codificarPadrao(BufferedImage image, String format) {
    ImageWriter writer = primeiroWriter("image/" + format);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
      output.flush();
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw falhaProcessamento(exception);
    } finally {
      writer.dispose();
    }
  }

  private void configurarJpeg444(IIOMetadata metadata) {
    if (metadata == null || metadata.isReadOnly() || !metadata.isStandardMetadataFormatSupported()) return;
    try {
      IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(JPEG_METADATA_FORMAT);
      NodeList components = root.getElementsByTagName("componentSpec");
      for (int index = 0; index < components.getLength(); index++) {
        IIOMetadataNode component = (IIOMetadataNode) components.item(index);
        component.setAttribute("HsamplingFactor", "1");
        component.setAttribute("VsamplingFactor", "1");
      }
      metadata.setFromTree(JPEG_METADATA_FORMAT, root);
    } catch (Exception exception) {
      throw new IllegalStateException("Encoder JPEG nao aceitou chroma 4:4:4", exception);
    }
  }

  private BufferedImage converterOpaca(BufferedImage source) {
    BufferedImage target = imagemVazia(source.getWidth(), source.getHeight(), false);
    Graphics2D graphics = target.createGraphics();
    try {
      configurarQualidade(graphics);
      graphics.drawImage(source, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private boolean possuiTransparencia(BufferedImage image) {
    if (!image.getColorModel().hasAlpha()) return false;
    Raster alpha = image.getAlphaRaster();
    if (alpha == null) return false;
    for (int y = 0; y < alpha.getHeight(); y++) {
      for (int x = 0; x < alpha.getWidth(); x++) {
        if (alpha.getSample(x, y, 0) < 255) return true;
      }
    }
    return false;
  }

  private FormatoSaida formatoSaida(String mimeType, boolean transparenciaNecessaria) {
    if ("image/webp".equals(mimeType)) return FormatoSaida.WEBP;
    if ("image/png".equals(mimeType) && transparenciaNecessaria) return FormatoSaida.PNG;
    return FormatoSaida.JPEG;
  }

  private boolean assinaturaCompativel(byte[] bytes, String mimeType) {
    if (bytes == null || bytes.length < 12) return false;
    if ("image/jpeg".equals(mimeType)) {
      return (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
          && (bytes[bytes.length - 2] & 0xff) == 0xff && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }
    if ("image/png".equals(mimeType)) {
      byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
      for (int index = 0; index < signature.length; index++) {
        if (bytes[index] != signature[index]) return false;
      }
      return true;
    }
    return "image/webp".equals(mimeType)
        && ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP");
  }

  private BufferedImage carregarMarcaDagua() {
    try (InputStream input = FotoUploadProcessor.class.getClassLoader().getResourceAsStream(WATERMARK_RESOURCE)) {
      if (input == null) throw new IllegalStateException("Asset oficial de marca-dagua ausente");
      byte[] bytes = input.readAllBytes();
      if (!WATERMARK_VERSION.equals(sha256(bytes))) {
        throw new IllegalStateException("SHA-256 do asset de marca-dagua divergente");
      }
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() != WATERMARK_WIDTH || image.getHeight() != WATERMARK_HEIGHT) {
        throw new IllegalStateException("Dimensoes do asset de marca-dagua divergentes");
      }
      return image;
    } catch (IOException exception) {
      throw new IllegalStateException("Asset oficial de marca-dagua nao pode ser lido", exception);
    }
  }

  private ImageWriter primeiroWriter(String mimeType) {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(mimeType);
    if (!writers.hasNext()) throw new IllegalStateException("Encoder indisponivel para " + mimeType);
    return writers.next();
  }

  private BufferedImage imagemVazia(int width, int height, boolean alpha) {
    return new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
  }

  private void configurarQualidade(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private String ascii(byte[] bytes, int offset, int length) {
    return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
  }

  private ResponseStatusException formatoInvalido() {
    return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "imagem corrompida ou nao suportada");
  }

  private ResponseStatusException falhaProcessamento(Exception cause) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "foto nao pode ser processada", cause);
  }

  private static void validarConfiguracao(MidiaUploadProperties properties) {
    if (properties.getMaxImagePixels() < 1 || properties.getMaxImageDimension() < 1
        || properties.getMaxProcessedSide() < 1
        || properties.getJpegQuality() <= 0 || properties.getJpegQuality() > 1
        || properties.getWebpQuality() <= 0 || properties.getWebpQuality() > 1) {
      throw new IllegalStateException("Configuracao do pipeline de fotos invalida");
    }
  }

  private enum FormatoSaida {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extensao;

    FormatoSaida(String mimeType, String extensao) {
      this.mimeType = mimeType;
      this.extensao = extensao;
    }

    String mimeType() {
      return mimeType;
    }

    String extensao() {
      return extensao;
    }
  }

  public record FotoProcessada(
      byte[] bytes,
      String mimeType,
      String extensao,
      int largura,
      int altura,
      String sha256,
      String sha256Origem,
      int pipelineVersao,
      String marcaDaguaVersao,
      OffsetDateTime processadoEm) {

    public FotoProcessada {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  public record FotoRestritaDerivada(
      byte[] bytes,
      String mimeType,
      String extensao,
      int largura,
      int altura,
      String sha256) {

    public FotoRestritaDerivada {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }
}
