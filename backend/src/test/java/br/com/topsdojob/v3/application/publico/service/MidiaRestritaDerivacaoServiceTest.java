package br.com.topsdojob.v3.application.publico.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.publico.anunciante.midia.FotoUploadProcessor;
import br.com.topsdojob.v3.application.publico.anunciante.midia.MidiaUploadProperties;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import br.com.topsdojob.v3.persistence.entity.midia.ArquivoMidiaEntity;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

class MidiaRestritaDerivacaoServiceTest {

  @Test
  void criaPreviewDeterministicoUmaVezESemExporOriginal() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    R2StorageProperties properties = properties();
    byte[] source = jpeg();
    UUID arquivoId = UUID.randomUUID();
    String sourceKey = properties.getPrivateMediaPrefix() + "origem.jpg";
    storage.put(StorageArea.PRIVATE_MEDIA, sourceKey, source, "image/jpeg");
    ArquivoMidiaEntity arquivo = arquivo(
        arquivoId,
        properties.getPrivateMediaBucket(),
        sourceKey,
        sha256(source));
    MidiaRestritaDerivacaoService service = service(storage, properties);

    var primeira = service.garantir(arquivo);
    var segunda = service.garantir(arquivo);
    var preview = service.resolverPreviewPublica(arquivo);

    assertThat(primeira.criada()).isTrue();
    assertThat(segunda.criada()).isFalse();
    assertThat(segunda.chavePublica()).isEqualTo(primeira.chavePublica());
    assertThat(primeira.chavePublica())
        .startsWith(properties.getPublicMediaPrefix() + "restritas-borradas/v1/")
        .endsWith(".jpg")
        .doesNotContain("origem");
    assertThat(storage.putIfAbsentCalls).isEqualTo(2);
    assertThat(storage.publicGetCalls).isEqualTo(1);
    assertThat(storage.count(StorageArea.PUBLIC_MEDIA)).isEqualTo(1);
    assertThat(preview.previewUrl()).contains("/restritas-borradas/v1/");
    assertThat(preview.previewUrl()).doesNotContain(sourceKey);
    assertThat(storage.exists(StorageArea.PRIVATE_MEDIA, sourceKey)).isTrue();
  }

  @Test
  void derivacaoExistenteDivergenteFalhaFechadoSemSobrescrever() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    R2StorageProperties properties = properties();
    byte[] source = jpeg();
    String sourceKey = properties.getPrivateMediaPrefix() + "origem.jpg";
    storage.put(StorageArea.PRIVATE_MEDIA, sourceKey, source, "image/jpeg");
    ArquivoMidiaEntity arquivo = arquivo(
        UUID.randomUUID(),
        properties.getPrivateMediaBucket(),
        sourceKey,
        sha256(source));
    MidiaRestritaDerivacaoService service = service(storage, properties);
    String previewKey = service.chavePublica(arquivo);
    byte[] divergent = new byte[] {9, 8, 7};
    storage.put(StorageArea.PUBLIC_MEDIA, previewKey, divergent, "image/jpeg");

    assertThatThrownBy(() -> service.garantir(arquivo))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("divergente");
    assertThat(storage.get(StorageArea.PUBLIC_MEDIA, previewKey).content()).isEqualTo(divergent);
  }

  @Test
  void checksumDivergenteFalhaFechadoSemCriarPreview() throws Exception {
    MemoryStorage storage = new MemoryStorage();
    R2StorageProperties properties = properties();
    byte[] source = jpeg();
    String sourceKey = properties.getPrivateMediaPrefix() + "origem.jpg";
    storage.put(StorageArea.PRIVATE_MEDIA, sourceKey, source, "image/jpeg");
    ArquivoMidiaEntity arquivo = arquivo(
        UUID.randomUUID(),
        properties.getPrivateMediaBucket(),
        sourceKey,
        "0".repeat(64));
    MidiaRestritaDerivacaoService service = service(storage, properties);

    assertThatThrownBy(() -> service.garantir(arquivo))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("checksum");
    assertThat(storage.count(StorageArea.PUBLIC_MEDIA)).isZero();
    assertThat(service.resolverPreviewPublica(arquivo).previewUrl()).isNull();
  }

  private MidiaRestritaDerivacaoService service(
      ObjectStorage storage,
      R2StorageProperties properties) {
    @SuppressWarnings("unchecked")
    ObjectProvider<ObjectStorage> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(storage);
    return new MidiaRestritaDerivacaoService(
        provider,
        properties,
        new FotoUploadProcessor(new MidiaUploadProperties()));
  }

  private R2StorageProperties properties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setPublicMediaBucket("publicas");
    properties.setPrivateMediaBucket("privadas");
    properties.setDocumentBucket("documentos");
    properties.setPublicMediaPrefix("hml/preprod/midias-aprovadas/");
    properties.setPrivateMediaPrefix("hml/preprod/midias-pendentes/");
    properties.setDocumentPrefix("hml/preprod/documentos/");
    properties.setPublicBaseUrl("https://public.example.invalid");
    return properties;
  }

  private ArquivoMidiaEntity arquivo(
      UUID id,
      String bucket,
      String key,
      String checksum) {
    ArquivoMidiaEntity arquivo = mock(ArquivoMidiaEntity.class);
    when(arquivo.getId()).thenReturn(id);
    when(arquivo.getStorageProvider()).thenReturn("R2");
    when(arquivo.getBucket()).thenReturn(bucket);
    when(arquivo.getChaveObjeto()).thenReturn(key);
    when(arquivo.getMimeType()).thenReturn("image/jpeg");
    when(arquivo.getSha256()).thenReturn(checksum);
    return arquivo;
  }

  private byte[] jpeg() throws Exception {
    BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    for (int x = 0; x < image.getWidth(); x += 8) {
      graphics.setColor(x % 16 == 0 ? Color.BLACK : Color.WHITE);
      graphics.fillRect(x, 0, 8, image.getHeight());
    }
    graphics.dispose();
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpg", output);
      return output.toByteArray();
    }
  }

  private String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static final class MemoryStorage implements ObjectStorage {
    private final Map<StorageArea, Map<String, StoredObject>> objects =
        new EnumMap<>(StorageArea.class);
    private int putIfAbsentCalls;
    private int publicGetCalls;

    private MemoryStorage() {
      for (StorageArea area : StorageArea.values()) {
        objects.put(area, new HashMap<>());
      }
    }

    private int count(StorageArea area) {
      return objects.get(area).size();
    }

    @Override
    public void put(StorageArea area, String key, byte[] content, String contentType) {
      objects.get(area).put(key, new StoredObject(content, contentType));
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        StorageArea area,
        String key,
        byte[] content,
        String contentType) {
      putIfAbsentCalls++;
      if (objects.get(area).containsKey(key)) {
        return ObjectWriteResult.ALREADY_EXISTS;
      }
      put(area, key, content, contentType);
      return ObjectWriteResult.CREATED;
    }

    @Override
    public boolean exists(StorageArea area, String key) {
      return objects.get(area).containsKey(key);
    }

    @Override
    public StoredObject get(StorageArea area, String key) {
      if (area == StorageArea.PUBLIC_MEDIA) publicGetCalls++;
      return objects.get(area).get(key);
    }

    @Override
    public void delete(StorageArea area, String key) {
      objects.get(area).remove(key);
    }

    @Override
    public URI temporaryGetUrl(StorageArea area, String key, Duration ttl) {
      return URI.create("https://private.example.invalid/signed");
    }

    @Override
    public Optional<URI> publicUrl(StorageArea area, String key) {
      return Optional.of(URI.create("https://public.example.invalid/" + key));
    }
  }
}
