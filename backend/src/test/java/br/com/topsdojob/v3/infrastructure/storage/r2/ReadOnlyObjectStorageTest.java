package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadOnlyObjectStorageTest {

  @Test
  void bloqueiaTodasAsMutacoesSemChamarDelegate() {
    CountingStorage delegate = new CountingStorage();
    ObjectStorage storage = new ReadOnlyObjectStorage(delegate);

    assertThatThrownBy(() -> storage.put(
        StorageArea.PUBLIC_MEDIA,
        "object.jpg",
        new byte[] {1},
        "image/jpeg"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> storage.putIfAbsent(
        StorageArea.PUBLIC_MEDIA,
        "object.jpg",
        new byte[] {1},
        "image/jpeg"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> storage.delete(StorageArea.PUBLIC_MEDIA, "object.jpg"))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThat(delegate.mutations).isZero();
  }

  private static final class CountingStorage implements ObjectStorage {
    private int mutations;

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      mutations++;
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area,
        String key,
        byte[] content,
        String contentType) {
      mutations++;
      return ObjectWriteResult.CREATED;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return false;
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      throw new IllegalStateException("objeto ausente");
    }

    @Override
    public void delete(StorageArea area, String key) {
      mutations++;
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://private.invalid/temporary");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      return Optional.empty();
    }
  }
}