package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

final class R2ObjectStorage implements ObjectStorage {

  private final R2StorageProperties properties;
  private final R2Operations operations;

  R2ObjectStorage(R2StorageProperties properties, R2Operations operations) {
    this.properties = properties;
    this.operations = operations;
  }

  @Override
  public void put(StorageArea area, String key, byte[] content, String contentType) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Conteudo do objeto obrigatorio");
    }
    Location location = location(area, key);
    operations.put(location.bucket(), key, content.clone(), contentType);
  }

  @Override
  public ObjectWriteResult putIfAbsent(
      StorageArea area,
      String key,
      byte[] content,
      String contentType) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Conteudo do objeto obrigatorio");
    }
    Location location = location(area, key);
    return operations.putIfAbsent(location.bucket(), key, content.clone(), contentType);
  }

  @Override
  public boolean exists(StorageArea area, String key) {
    Location location = location(area, key);
    return operations.exists(location.bucket(), key);
  }

  @Override
  public StoredObject get(StorageArea area, String key) {
    Location location = location(area, key);
    return operations.get(location.bucket(), key);
  }

  @Override
  public void delete(StorageArea area, String key) {
    Location location = location(area, key);
    operations.delete(location.bucket(), key);
  }

  @Override
  public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
    Location location = location(area, key);
    Duration effectiveTtl = ttl == null
        ? Duration.ofSeconds(properties.getSignedUrlTtlSeconds())
        : ttl;
    if (effectiveTtl.isNegative() || effectiveTtl.isZero()
        || effectiveTtl.compareTo(Duration.ofDays(7)) > 0) {
      throw new IllegalArgumentException("TTL temporario fora do intervalo permitido");
    }
    return operations.presignGet(location.bucket(), key, effectiveTtl);
  }

  @Override
  public Optional<URI> publicUrl(StorageArea area, String key) {
    Location location = location(area, key);
    if (area != StorageArea.PUBLIC_MEDIA) {
      return Optional.empty();
    }
    String base = properties.getPublicBaseUrl();
    if (base == null || base.isBlank()) {
      return Optional.empty();
    }
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return Optional.of(URI.create(normalizedBase + "/" + R2UrlCodec.encodePath(key)));
  }

  private Location location(StorageArea area, String key) {
    if (area == null) {
      throw new IllegalArgumentException("Area de storage obrigatoria");
    }
    if (key == null || key.isBlank() || key.startsWith("/") || key.contains("..")
        || key.contains("\\") || key.contains("?") || key.contains("#")) {
      throw new IllegalArgumentException("Chave de objeto invalida");
    }
    Location location = switch (area) {
      case PUBLIC_MEDIA -> new Location(
          properties.getPublicMediaBucket(), properties.getPublicMediaPrefix());
      case PRIVATE_MEDIA -> new Location(
          properties.getPrivateMediaBucket(), properties.getPrivateMediaPrefix());
      case PRIVATE_DOCUMENT -> new Location(
          properties.getDocumentBucket(), properties.getDocumentPrefix());
    };
    if (!key.startsWith(location.prefix()) || key.length() == location.prefix().length()) {
      throw new IllegalArgumentException("Chave fora do prefixo autorizado para a area");
    }
    return location;
  }

  private record Location(String bucket, String prefix) {
  }
}
