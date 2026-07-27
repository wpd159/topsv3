package br.com.topsdojob.v3.application.admin.documento;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminKycThumbnailProcessor {

  private static final int MAX_SIDE = 480;
  private static final int CACHE_LIMIT = 256;
  private static final float JPEG_QUALITY = 0.82f;
  private static final float PDF_DPI = 110f;

  private final Map<CacheKey, Thumbnail> cache = java.util.Collections.synchronizedMap(
      new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Thumbnail> eldest) {
          return size() > CACHE_LIMIT;
        }
      });

  public Thumbnail processar(
      UUID arquivoId,
      String sha256,
      String mimeType,
      Supplier<byte[]> source) {
    CacheKey key = new CacheKey(arquivoId, sha256 == null ? "" : sha256);
    synchronized (cache) {
      Thumbnail cached = cache.get(key);
      if (cached != null) {
        return cached;
      }
      Thumbnail generated = gerar(mimeType, source.get());
      cache.put(key, generated);
      return generated;
    }
  }

  private Thumbnail gerar(String mimeType, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "arquivo do documento indisponivel");
    }
    try {
      BufferedImage source = "application/pdf".equalsIgnoreCase(mimeType)
          ? primeiraPagina(bytes)
          : ImageIO.read(new ByteArrayInputStream(bytes));
      if (source == null) {
        throw formatoInvalido();
      }
      BufferedImage resized = redimensionar(source);
      byte[] thumbnail = escreverJpeg(resized);
      return new Thumbnail(thumbnail, "image/jpeg", "\"" + sha256(thumbnail) + "\"");
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw formatoInvalido();
    }
  }

  private BufferedImage primeiraPagina(byte[] bytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      if (document.getNumberOfPages() < 1) {
        throw formatoInvalido();
      }
      return new PDFRenderer(document).renderImageWithDPI(0, PDF_DPI, ImageType.RGB);
    }
  }

  private BufferedImage redimensionar(BufferedImage source) {
    double scale = Math.min(1d, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private byte[] escreverJpeg(BufferedImage image) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      ImageWriteParam params = writer.getDefaultWriteParam();
      if (params.canWriteCompressed()) {
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
      }
      writer.write(null, new IIOImage(image, null, null), params);
      return output.toByteArray();
    } finally {
      writer.dispose();
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private ResponseStatusException formatoInvalido() {
    return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "documento nao permite miniatura");
  }

  private record CacheKey(UUID arquivoId, String sha256) {
  }

  public record Thumbnail(byte[] content, String contentType, String etag) {
    public Thumbnail {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }
  }
}
