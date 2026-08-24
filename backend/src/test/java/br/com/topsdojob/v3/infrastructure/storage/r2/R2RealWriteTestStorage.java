package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class R2RealWriteTestStorage implements ObjectStorage, AutoCloseable {

  private final ObjectStorage delegate;
  private final String prefix;
  private final Set<Location> created = new LinkedHashSet<>();

  R2RealWriteTestStorage(ObjectStorage delegate, String prefix) {
    this.delegate = Objects.requireNonNull(delegate, "storage obrigatorio");
    this.prefix = Objects.requireNonNull(prefix, "prefixo obrigatorio");
  }

  @Override
  public void put(StorageArea area, String key, byte[] content, String contentType) {
    validate(area, key);
    if (delegate.exists(area, key)) {
      throw new IllegalStateException("Teste real nao pode sobrescrever objeto existente");
    }
    Location location = new Location(area, key);
    created.add(location);
    delegate.put(area, key, content, contentType);
  }

  @Override
  public ObjectWriteResult putIfAbsent(
      StorageArea area,
      String key,
      byte[] content,
      String contentType) {
    validate(area, key);
    Location location = new Location(area, key);
    created.add(location);
    ObjectWriteResult result = delegate.putIfAbsent(area, key, content, contentType);
    if (result == ObjectWriteResult.ALREADY_EXISTS) {
      created.remove(location);
    }
    return result;
  }

  @Override
  public boolean exists(StorageArea area, String key) {
    validate(area, key);
    return delegate.exists(area, key);
  }

  @Override
  public StoredObject get(StorageArea area, String key) {
    validate(area, key);
    return delegate.get(area, key);
  }

  @Override
  public void delete(StorageArea area, String key) {
    validate(area, key);
    Location location = new Location(area, key);
    if (!created.contains(location)) {
      throw new IllegalArgumentException("Teste real so pode excluir objeto criado na execucao");
    }
    delegate.delete(area, key);
    if (delegate.exists(area, key)) {
      throw new IllegalStateException("Objeto permaneceu apos cleanup");
    }
    created.remove(location);
  }

  @Override
  public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
    validate(area, key);
    return delegate.temporaryGetUrl(area, key, ttl);
  }

  @Override
  public Optional<URI> publicUrl(StorageArea area, String key) {
    validate(area, key);
    return delegate.publicUrl(area, key);
  }

  @Override
  public void close() {
    List<RuntimeException> failures = new ArrayList<>();
    List<Location> pending = new ArrayList<>(created);
    for (int index = pending.size() - 1; index >= 0; index--) {
      Location location = pending.get(index);
      try {
        delegate.delete(location.area(), location.key());
        if (delegate.exists(location.area(), location.key())) {
          throw new IllegalStateException("Residuo detectado apos cleanup");
        }
        created.remove(location);
      } catch (RuntimeException exception) {
        failures.add(exception);
      }
    }
    if (!failures.isEmpty() || !created.isEmpty()) {
      IllegalStateException failure = new IllegalStateException(
          "Cleanup do teste real nao removeu todos os objetos");
      failures.forEach(failure::addSuppressed);
      throw failure;
    }
  }

  private void validate(StorageArea area, String key) {
    Objects.requireNonNull(area, "area obrigatoria");
    R2RealWriteTestGate.validateKey(prefix, key);
  }

  private record Location(StorageArea area, String key) {
  }
}