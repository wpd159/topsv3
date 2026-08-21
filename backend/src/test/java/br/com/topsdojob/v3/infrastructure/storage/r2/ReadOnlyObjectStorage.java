package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

final class ReadOnlyObjectStorage implements ObjectStorage {

  private final ObjectStorage delegate;

  ReadOnlyObjectStorage(ObjectStorage delegate) {
    this.delegate = Objects.requireNonNull(delegate, "storage obrigatorio");
  }

  @Override
  public void put(StorageArea area, String key, byte[] content, String contentType) {
    throw mutationBlocked();
  }

  @Override
  public ObjectWriteResult putIfAbsent(
      StorageArea area,
      String key,
      byte[] content,
      String contentType) {
    throw mutationBlocked();
  }

  @Override
  public boolean exists(StorageArea area, String key) {
    return delegate.exists(area, key);
  }

  @Override
  public StoredObject get(StorageArea area, String key) {
    return delegate.get(area, key);
  }

  @Override
  public void delete(StorageArea area, String key) {
    throw mutationBlocked();
  }

  @Override
  public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
    return delegate.temporaryGetUrl(area, key, ttl);
  }

  @Override
  public Optional<URI> publicUrl(StorageArea area, String key) {
    return delegate.publicUrl(area, key);
  }

  private UnsupportedOperationException mutationBlocked() {
    return new UnsupportedOperationException("Storage configurado como somente leitura");
  }
}