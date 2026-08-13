package br.com.topsdojob.v3.application.importacao;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.importacao.midia.GeradorChaveDestinoMidiaMigracao;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;

public final class PrivateMediaObjectTransport {

  static {
    ImageIO.scanForPlugins();
  }

  public Result transport(
      ObjectStorage source,
      ObjectStorage destination,
      Candidate candidate) {
    Objects.requireNonNull(source, "storage de origem obrigatorio");
    Objects.requireNonNull(destination, "storage de destino obrigatorio");
    Objects.requireNonNull(candidate, "candidato obrigatorio");
    candidate.validate();

    try {
      if (!source.exists(candidate.sourceArea(), candidate.sourceKey())) {
        return Result.quarantined(candidate, "OBJETO_ORIGEM_AUSENTE");
      }

      StoredObject sourceObject = source.get(candidate.sourceArea(), candidate.sourceKey());
      byte[] content = sourceObject.content();
      DetectedMedia detected = detect(content);
      if (detected == null || detected.type() != candidate.type()) {
        return Result.quarantined(candidate, "TIPO_OU_ASSINATURA_DIVERGENTE");
      }

      String checksum = sha256(content);
      String destinationKey = GeradorChaveDestinoMidiaMigracao.caminhoAnuncio(
          candidate.destinationPrefix(),
          Long.toString(candidate.sourceAdId()),
          candidate.logicalMediaHash(),
          candidate.variant().manifestValue(),
          checksum,
          detected.extension());

      if (destination.exists(StorageArea.PRIVATE_MEDIA, destinationKey)) {
        return validateDestination(
            destination, candidate, destinationKey, checksum, content.length, detected, false);
      }

      ObjectWriteResult write = destination.putIfAbsent(
          StorageArea.PRIVATE_MEDIA,
          destinationKey,
          content,
          detected.contentType());
      return validateDestination(
          destination,
          candidate,
          destinationKey,
          checksum,
          content.length,
          detected,
          write == ObjectWriteResult.CREATED);
    } catch (RuntimeException exception) {
      return Result.blocked(candidate, "FALHA_STORAGE_SANITIZADA");
    }
  }

  public List<Result> normalizePrincipals(List<Result> results) {
    Objects.requireNonNull(results, "resultados obrigatorios");
    Map<LogicalMedia, List<Result>> groups = results.stream()
        .collect(java.util.stream.Collectors.groupingBy(
            result -> new LogicalMedia(result.sourceAdId(), result.logicalMediaHash()),
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()));

    Map<LogicalMedia, Result> selected = new LinkedHashMap<>();
    Comparator<Result> preference = Comparator
        .comparing((Result result) -> !result.primary())
        .thenComparingInt(result -> variantPriority(result.variant()))
        .thenComparing(Result::sourceTable)
        .thenComparing(Result::sourceId);
    for (Map.Entry<LogicalMedia, List<Result>> group : groups.entrySet()) {
      group.getValue().stream()
          .filter(this::usable)
          .min(preference)
          .ifPresent(result -> selected.put(group.getKey(), result));
    }

    return results.stream()
        .map(result -> result.withPrimary(
            result == selected.get(new LogicalMedia(
                result.sourceAdId(),
                result.logicalMediaHash()))))
        .toList();
  }

  private boolean usable(Result result) {
    return result.status() == Status.MIGRADA || result.status() == Status.PRESERVADA;
  }

  private int variantPriority(Variant variant) {
    return switch (variant) {
      case ORIGINAL -> 0;
      case PROCESSADO -> 1;
      case DERIVADO -> 2;
      case LEGADO -> 3;
      case PREVIEW -> 4;
      case THUMBNAIL -> 5;
    };
  }

  private Result validateDestination(
      ObjectStorage destination,
      Candidate candidate,
      String destinationKey,
      String checksum,
      long expectedSize,
      DetectedMedia detected,
      boolean created) {
    if (!destination.exists(StorageArea.PRIVATE_MEDIA, destinationKey)) {
      return Result.blocked(candidate, "OBJETO_DESTINO_AUSENTE_APOS_COPIA");
    }

    StoredObject stored = destination.get(StorageArea.PRIVATE_MEDIA, destinationKey);
    byte[] storedContent = stored.content();
    if (storedContent.length != expectedSize || !sha256(storedContent).equals(checksum)) {
      return Result.blocked(candidate, "CHECKSUM_OU_TAMANHO_DESTINO_DIVERGENTE");
    }
    if (!stored.contentType().equals(detected.contentType())) {
      return Result.blocked(candidate, "MIME_DESTINO_DIVERGENTE");
    }
    if (destination.publicUrl(StorageArea.PRIVATE_MEDIA, destinationKey).isPresent()) {
      return Result.blocked(candidate, "MIDIA_PENDENTE_COM_URL_PUBLICA");
    }
    return Result.success(candidate, destinationKey, checksum, storedContent.length, detected, created);
  }

  private DetectedMedia detect(byte[] content) {
    if (content == null || content.length < 12) {
      return null;
    }
    if (jpeg(content)) {
      return image(content, "image/jpeg", "jpg");
    }
    if (png(content)) {
      return image(content, "image/png", "png");
    }
    if (webp(content)) {
      return image(content, "image/webp", "webp");
    }
    if (gif(content)) {
      return image(content, "image/gif", "gif");
    }
    String brand = isoBrand(content);
    if (brand != null && isHeicBrand(brand)) {
      return new DetectedMedia(MediaType.FOTO, "image/heic", "heic", null, null);
    }
    if (brand != null && isVideoBrand(brand)) {
      String contentType = "qt  ".equals(brand) ? "video/quicktime" : "video/mp4";
      String extension = "qt  ".equals(brand) ? "mov" : "mp4";
      return new DetectedMedia(MediaType.VIDEO, contentType, extension, null, null);
    }
    if (webm(content)) {
      return new DetectedMedia(MediaType.VIDEO, "video/webm", "webm", null, null);
    }
    return null;
  }

  private DetectedMedia image(byte[] content, String contentType, String extension) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
        return null;
      }
      return new DetectedMedia(
          MediaType.FOTO, contentType, extension, image.getWidth(), image.getHeight());
    } catch (Exception exception) {
      return null;
    }
  }

  private boolean jpeg(byte[] content) {
    if ((content[0] & 0xff) != 0xff || (content[1] & 0xff) != 0xd8) {
      return false;
    }
    for (int index = content.length - 2; index >= 2; index--) {
      if ((content[index] & 0xff) == 0xff && (content[index + 1] & 0xff) == 0xd9) {
        return true;
      }
    }
    return false;
  }

  private boolean png(byte[] content) {
    byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    for (int index = 0; index < signature.length; index++) {
      if (content[index] != signature[index]) {
        return false;
      }
    }
    return true;
  }

  private boolean webp(byte[] content) {
    return ascii(content, 0, 4).equals("RIFF") && ascii(content, 8, 4).equals("WEBP");
  }

  private boolean gif(byte[] content) {
    String signature = ascii(content, 0, 6);
    return signature.equals("GIF87a") || signature.equals("GIF89a");
  }

  private boolean webm(byte[] content) {
    return (content[0] & 0xff) == 0x1a
        && (content[1] & 0xff) == 0x45
        && (content[2] & 0xff) == 0xdf
        && (content[3] & 0xff) == 0xa3;
  }

  private String isoBrand(byte[] content) {
    if (content.length < 12 || !ascii(content, 4, 4).equals("ftyp")) {
      return null;
    }
    return ascii(content, 8, 4);
  }

  private boolean isHeicBrand(String brand) {
    return switch (brand.toLowerCase(Locale.ROOT)) {
      case "heic", "heix", "hevc", "hevx", "mif1", "msf1" -> true;
      default -> false;
    };
  }

  private boolean isVideoBrand(String brand) {
    return switch (brand.toLowerCase(Locale.ROOT)) {
      case "isom", "iso2", "iso3", "iso4", "iso5", "iso6",
          "mp41", "mp42", "avc1", "dash", "msnv", "xavc",
          "m4v ", "m4a ", "qt  ", "3gp4", "3gp5", "3gp6", "3g2a", "3g2b" -> true;
      default -> false;
    };
  }

  private String ascii(byte[] content, int offset, int length) {
    if (offset < 0 || length < 0 || offset + length > content.length) {
      return "";
    }
    return new String(content, offset, length, java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  public record Candidate(
      long sourceAdId,
      String logicalMediaHash,
      String sourceTable,
      String sourceId,
      MediaType type,
      Variant variant,
      boolean primary,
      String referenceHash,
      StorageArea sourceArea,
      String sourceKey,
      int order,
      String destinationPrefix) {

    private void validate() {
      if (sourceAdId < 1
          || !hex64(logicalMediaHash)
          || !hex64(referenceHash)
          || sourceTable == null
          || !sourceTable.matches("protected_media_assets|anuncio_fotos|anuncio_videos")
          || sourceId == null
          || sourceId.isBlank()
          || type == null
          || variant == null
          || sourceArea == null
          || sourceArea == StorageArea.PRIVATE_DOCUMENT
          || sourceKey == null
          || sourceKey.isBlank()
          || order < 0
          || destinationPrefix == null
          || !destinationPrefix.startsWith("hml/")
          || !destinationPrefix.endsWith("/")
          || destinationPrefix.contains("..")) {
        throw new IllegalArgumentException("candidato de midia privada invalido");
      }
    }

    private static boolean hex64(String value) {
      return value != null && value.matches("[0-9a-f]{64}");
    }
  }

  public record Result(
      long sourceAdId,
      String logicalMediaHash,
      String sourceTable,
      String sourceId,
      MediaType type,
      Variant variant,
      boolean primary,
      String referenceHash,
      String destinationKey,
      String checksum,
      long size,
      String contentType,
      Integer width,
      Integer height,
      int order,
      Status status,
      String reason) {

    private Result withPrimary(boolean normalizedPrimary) {
      return new Result(
          sourceAdId,
          logicalMediaHash,
          sourceTable,
          sourceId,
          type,
          variant,
          normalizedPrimary,
          referenceHash,
          destinationKey,
          checksum,
          size,
          contentType,
          width,
          height,
          order,
          status,
          reason);
    }

    private static Result success(
        Candidate candidate,
        String destinationKey,
        String checksum,
        long size,
        DetectedMedia detected,
        boolean created) {
      return new Result(
          candidate.sourceAdId(),
          candidate.logicalMediaHash(),
          candidate.sourceTable(),
          candidate.sourceId(),
          candidate.type(),
          candidate.variant(),
          candidate.primary(),
          candidate.referenceHash(),
          destinationKey,
          checksum,
          size,
          detected.contentType(),
          detected.width(),
          detected.height(),
          candidate.order(),
          created ? Status.MIGRADA : Status.PRESERVADA,
          null);
    }

    private static Result quarantined(Candidate candidate, String reason) {
      return failure(candidate, Status.QUARENTENA, reason);
    }

    private static Result blocked(Candidate candidate, String reason) {
      return failure(candidate, Status.BLOQUEADA, reason);
    }

    private static Result failure(Candidate candidate, Status status, String reason) {
      return new Result(
          candidate.sourceAdId(),
          candidate.logicalMediaHash(),
          candidate.sourceTable(),
          candidate.sourceId(),
          candidate.type(),
          candidate.variant(),
          candidate.primary(),
          candidate.referenceHash(),
          null,
          null,
          0,
          null,
          null,
          null,
          candidate.order(),
          status,
          reason);
    }

    public Optional<String> destinationKeyOptional() {
      return Optional.ofNullable(destinationKey);
    }
  }

  public enum MediaType {
    FOTO,
    VIDEO
  }

  public enum Variant {
    ORIGINAL,
    PREVIEW,
    THUMBNAIL,
    DERIVADO,
    PROCESSADO,
    LEGADO;

    public String manifestValue() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  public enum Status {
    MIGRADA,
    PRESERVADA,
    QUARENTENA,
    BLOQUEADA
  }

  private record DetectedMedia(
      MediaType type,
      String contentType,
      String extension,
      Integer width,
      Integer height) {
  }

  private record LogicalMedia(long sourceAdId, String logicalMediaHash) {
  }
}
