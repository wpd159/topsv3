package br.com.topsdojob.v3.infrastructure.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface ObjectStorage {

  void put(StorageArea area, String key, byte[] content, String contentType);

  ObjectWriteResult putIfAbsent(
      StorageArea area,
      String key,
      byte[] content,
      String contentType);

  boolean exists(StorageArea area, String key);

  StoredObject get(StorageArea area, String key);

  void delete(StorageArea area, String key);

  URI temporaryGetUrl(StorageArea area, String key, Duration ttl);

  Optional<URI> publicUrl(StorageArea area, String key);

  default StoredObjectPage list(
      StorageArea area,
      String prefix,
      String continuationToken,
      int maxKeys) {
    throw new UnsupportedOperationException("Listagem de objetos nao suportada");
  }
}
