package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class R2RealWriteTestStorageTest {

  @Test
  void cleanupExecutaAposSucesso() {
    MemoryStorage delegate = new MemoryStorage();
    String prefix = isolatedPrefix();

    try (R2RealWriteTestStorage storage = new R2RealWriteTestStorage(delegate, prefix)) {
      storage.put(StorageArea.PUBLIC_MEDIA, prefix + "success.jpg", new byte[] {1}, "image/jpeg");
    }

    assertThat(delegate.objects).isEmpty();
    assertThat(delegate.deleteCalls).isEqualTo(1);
  }

  @Test
  void cleanupExecutaQuandoCorpoDoTesteFalha() {
    MemoryStorage delegate = new MemoryStorage();
    String prefix = isolatedPrefix();

    assertThatThrownBy(() -> {
      try (R2RealWriteTestStorage storage = new R2RealWriteTestStorage(delegate, prefix)) {
        storage.put(StorageArea.PUBLIC_MEDIA, prefix + "failure.jpg", new byte[] {1}, "image/jpeg");
        throw new IllegalStateException("falha controlada do teste");
      }
    }).hasMessage("falha controlada do teste");

    assertThat(delegate.objects).isEmpty();
    assertThat(delegate.deleteCalls).isEqualTo(1);
  }

  @Test
  void cleanupExecutaQuandoPutFalhaDepoisDeCriarObjeto() {
    MemoryStorage delegate = new MemoryStorage();
    delegate.failAfterPut = true;
    String prefix = isolatedPrefix();

    assertThatThrownBy(() -> {
      try (R2RealWriteTestStorage storage = new R2RealWriteTestStorage(delegate, prefix)) {
        storage.put(StorageArea.PRIVATE_MEDIA, prefix + "exception.jpg", new byte[] {1}, "image/jpeg");
      }
    }).hasMessage("falha parcial simulada");

    assertThat(delegate.objects).isEmpty();
    assertThat(delegate.deleteCalls).isEqualTo(1);
  }

  @Test
  void verificacaoFinalDetectaResiduo() {
    MemoryStorage delegate = new MemoryStorage();
    delegate.keepObjectOnDelete = true;
    String prefix = isolatedPrefix();
    R2RealWriteTestStorage storage = new R2RealWriteTestStorage(delegate, prefix);
    storage.put(StorageArea.PRIVATE_DOCUMENT, prefix + "residue.jpg", new byte[] {1}, "image/jpeg");

    assertThatThrownBy(storage::close)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cleanup");
    assertThat(delegate.objects).hasSize(1);
  }

  @Test
  void naoSobrescreveObjetoPreexistenteNemExcluiForaDaExecucao() {
    MemoryStorage delegate = new MemoryStorage();
    String prefix = isolatedPrefix();
    String key = prefix + "existing.jpg";
    delegate.put(StorageArea.PUBLIC_MEDIA, key, new byte[] {1}, "image/jpeg");

    try (R2RealWriteTestStorage storage = new R2RealWriteTestStorage(delegate, prefix)) {
      assertThatThrownBy(() -> storage.put(
          StorageArea.PUBLIC_MEDIA,
          key,
          new byte[] {2},
          "image/jpeg"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("sobrescrever");
      assertThatThrownBy(() -> storage.delete(StorageArea.PUBLIC_MEDIA, key))
          .isInstanceOf(IllegalArgumentException.class);
    }

    assertThat(delegate.get(StorageArea.PUBLIC_MEDIA, key).content()).containsExactly(1);
  }

  private String isolatedPrefix() {
    return "test-runs/" + UUID.randomUUID() + "/";
  }

  private static final class MemoryStorage implements ObjectStorage {
    private final Map<String, StoredObject> objects = new LinkedHashMap<>();
    private boolean failAfterPut;
    private boolean keepObjectOnDelete;
    private int deleteCalls;

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      objects.put(location(area, key), new StoredObject(content.clone(), contentType));
      if (failAfterPut) {
        failAfterPut = false;
        throw new IllegalStateException("falha parcial simulada");
      }
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area,
        String key,
        byte[] content,
        String contentType) {
      String location = location(area, key);
      if (objects.containsKey(location)) {
        return ObjectWriteResult.ALREADY_EXISTS;
      }
      put(area, key, content, contentType);
      return ObjectWriteResult.CREATED;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return objects.containsKey(location(area, key));
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      StoredObject object = objects.get(location(area, key));
      if (object == null) {
        throw new IllegalStateException("objeto ausente");
      }
      return object;
    }

    @Override
    public void delete(StorageArea area, String key) {
      deleteCalls++;
      if (!keepObjectOnDelete) {
        objects.remove(location(area, key));
      }
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://private.invalid/temporary");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      return Optional.empty();
    }

    private String location(StorageArea area, String key) {
      return area + ":" + key;
    }
  }
}