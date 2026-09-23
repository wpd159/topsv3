package br.com.topsdojob.v3.application.blog;

import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadValidator.MidiaValidada;
import java.awt.AlphaComposite;
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
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BlogImagemProcessor {

  private static final int MAX_SIDE = 2400;

  private final MidiaUploadValidator validator;

  public BlogImagemProcessor(MidiaUploadValidator validator) {
    this.validator = validator;
  }

  public ImagemProcessada processar(MultipartFile file, String tipo) {
    MidiaValidada validada = validator.validar(file);
    if (validada.video()) {
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "a imagem editorial deve ser JPG, PNG ou WebP");
    }
    try {
      BufferedImage original = ImageIO.read(new ByteArrayInputStream(validada.bytes()));
      if (original == null) {
        throw invalida();
      }
      BufferedImage redimensionada = redimensionar(original);
      boolean transparencia = redimensionada.getColorModel().hasAlpha();
      String formato = transparencia ? "png" : "jpg";
      String mime = transparencia ? "image/png" : "image/jpeg";
      byte[] bytes = transparencia ? escreverPng(redimensionada)
          : escreverJpeg(redimensionada, "CAPA".equals(tipo) ? 0.8f : 0.9f);
      return new ImagemProcessada(
          bytes,
          mime,
          formato,
          sha256(bytes),
          redimensionada.getWidth(),
          redimensionada.getHeight());
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (IOException exception) {
      throw invalida();
    }
  }

  private BufferedImage redimensionar(BufferedImage original) {
    int width = original.getWidth();
    int height = original.getHeight();
    double scale = Math.min(1d, (double) MAX_SIDE / Math.max(width, height));
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));
    boolean alpha = original.getColorModel().hasAlpha();
    BufferedImage target = new BufferedImage(
        targetWidth,
        targetHeight,
        alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setComposite(AlphaComposite.Src);
      if (!alpha) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, targetWidth, targetHeight);
      }
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private byte[] escreverPng(BufferedImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", output)) {
      throw invalida();
    }
    return output.toByteArray();
  }

  private byte[] escreverJpeg(BufferedImage image, float quality) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    if (!writers.hasNext()) {
      throw invalida();
    }
    ImageWriter writer = writers.next();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(imageOutput);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), param);
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

  private ResponseStatusException invalida() {
    return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "imagem editorial invalida");
  }

  public record ImagemProcessada(
      byte[] bytes,
      String mimeType,
      String extensao,
      String sha256,
      int largura,
      int altura) {
  }
}
