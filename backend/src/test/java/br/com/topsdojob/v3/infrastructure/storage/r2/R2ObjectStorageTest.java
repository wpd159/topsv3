package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R2ObjectStorageTest {

  private RecordingOperations operations;
  private R2ObjectStorage storage;

  @BeforeEach
  void setUp() {
    R2StorageProperties properties = configuredProperties();
    operations = new RecordingOperations();
    storage = new R2ObjectStorage(properties, operations);
  }

  @Test
  void roteiaCadaAreaParaBucketEPrefixoExclusivos() {
    storage.put(
        StorageArea.PUBLIC_MEDIA,
        "hml/midias-aprovadas/teste.jpg",
        new byte[] {1},
        "image/jpeg");
    storage.exists(StorageArea.PRIVATE_MEDIA, "hml/midias-pendentes/teste.jpg");
    storage.get(StorageArea.PRIVATE_DOCUMENT, "hml/documentos/teste.pdf");

    assertThat(operations.calls).containsExactly(
        "PUT:public:hml/midias-aprovadas/teste.jpg",
        "HEAD:private:hml/midias-pendentes/teste.jpg",
        "GET:documents:hml/documentos/teste.pdf");
  }

  @Test
  void rejeitaChaveForaDoPrefixoAntesDeAcessarR2() {
    assertThatThrownBy(() -> storage.exists(StorageArea.PUBLIC_MEDIA, "producao/teste.jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fora do prefixo");
    assertThatThrownBy(() -> storage.delete(StorageArea.PRIVATE_MEDIA, "hml/documentos/teste.pdf"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fora do prefixo");
    assertThatThrownBy(() -> storage.get(StorageArea.PRIVATE_DOCUMENT, "hml/documentos/../segredo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalida");

    assertThat(operations.calls).isEmpty();
  }

  @Test
  void somenteMidiaAprovadaPodeTerUrlPublica() {
    assertThat(storage.publicUrl(
        StorageArea.PUBLIC_MEDIA,
        "hml/midias-aprovadas/foto de teste.jpg"))
        .contains(URI.create("https://media-hml.invalid/hml/midias-aprovadas/foto%20de%20teste.jpg"));
    assertThat(storage.publicUrl(
        StorageArea.PRIVATE_MEDIA,
        "hml/midias-pendentes/foto.jpg"))
        .isEmpty();
    assertThat(storage.publicUrl(
        StorageArea.PRIVATE_DOCUMENT,
        "hml/documentos/doc.pdf"))
        .isEmpty();
  }

  @Test
  void midiaPromovidaPermaneceSemUrlQuandoBasePublicaNaoEstaConfigurada() {
    R2StorageProperties properties = configuredProperties();
    properties.setPublicBaseUrl(null);
    R2ObjectStorage storageSemDominio = new R2ObjectStorage(properties, operations);

    assertThat(storageSemDominio.publicUrl(
        StorageArea.PUBLIC_MEDIA,
        "hml/midias-aprovadas/foto.jpg"))
        .isEmpty();
  }

  @Test
  void origemPublicaPreservadaExigeConfiguracaoCompletaESegura() {
    R2StorageProperties incompleta = configuredProperties();
    incompleta.setPreservedPublicMediaBucket("legacy-public");

    assertThatThrownBy(incompleta::validateConfigured)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("preserved-public-media-prefix");

    R2StorageProperties completa = configuredProperties();
    completa.setPreservedPublicMediaBucket("legacy-public");
    completa.setPreservedPublicMediaPrefix("anuncios/fotos/original/");
    completa.setPreservedPublicBaseUrl("https://public-origin.invalid");

    completa.validateConfigured();
  }

  @Test
  void limitaValidadeDaUrlTemporaria() {
    assertThatThrownBy(() -> storage.temporaryGetUrl(
        StorageArea.PRIVATE_MEDIA,
        "hml/midias-pendentes/foto.jpg",
        Duration.ofDays(8)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TTL");
    assertThat(operations.calls).isEmpty();
  }

  private static R2StorageProperties configuredProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint("https://account.r2.cloudflarestorage.com");
    properties.setAccessKey("access");
    properties.setSigningValue("valor-de-assinatura-ficticio");
    properties.setPublicMediaBucket("public");
    properties.setPrivateMediaBucket("private");
    properties.setDocumentBucket("documents");
    properties.setPublicBaseUrl("https://media-hml.invalid");
    return properties;
  }

  private static final class RecordingOperations implements R2Operations {

    private final List<String> calls = new ArrayList<>();

    @Override
    public void put(String bucket, String key, byte[] content, String contentType) {
      calls.add("PUT:" + bucket + ":" + key);
    }

    @Override
    public boolean exists(String bucket, String key) {
      calls.add("HEAD:" + bucket + ":" + key);
      return true;
    }

    @Override
    public StoredObject get(String bucket, String key) {
      calls.add("GET:" + bucket + ":" + key);
      return new StoredObject(new byte[] {1}, "application/octet-stream");
    }

    @Override
    public void delete(String bucket, String key) {
      calls.add("DELETE:" + bucket + ":" + key);
    }

    @Override
    public URI presignGet(String bucket, String key, Duration ttl) {
      calls.add("PRESIGN:" + bucket + ":" + key);
      return URI.create("https://signed.invalid/object");
    }
  }
}
