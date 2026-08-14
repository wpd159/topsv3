package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.Origem;
import br.com.topsdojob.v3.importacao.midia.ManifestoMidiaFaseCinco.TipoOrigem;
import br.com.topsdojob.v3.infrastructure.storage.ObjectWriteResult;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.StoredObject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FonteR2LegadaSomenteLeituraTest {

  @Test
  void leChaveLegadaForaDeHmlSomenteComHeadEGet() {
    RecordingOperations operations = new RecordingOperations();
    var fonte = new FonteR2LegadaSomenteLeitura(operations, buckets());

    StoredObject objeto = fonte.carregar(new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PUBLIC_MEDIA,
        "uploads/legado/foto.jpg"));

    assertThat(objeto.content()).containsExactly(1, 2, 3);
    assertThat(operations.chamadas).containsExactly(
        "HEAD:public-source:uploads/legado/foto.jpg",
        "GET:public-source:uploads/legado/foto.jpg");
  }

  @Test
  void rejeitaChaveInvalidaOuComBucketInjetadoAntesDoR2() {
    RecordingOperations operations = new RecordingOperations();
    var fonte = new FonteR2LegadaSomenteLeitura(operations, buckets());

    assertThatThrownBy(() -> fonte.carregar(new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PUBLIC_MEDIA,
        "../foto.jpg")))
        .isInstanceOf(FonteMidiaMigracao.OrigemMidiaInvalidaException.class);
    assertThatThrownBy(() -> fonte.carregar(new Origem(
        TipoOrigem.OBJECT_STORAGE,
        StorageArea.PUBLIC_MEDIA,
        "public-source/foto.jpg")))
        .isInstanceOf(FonteMidiaMigracao.OrigemMidiaInvalidaException.class);

    assertThat(operations.chamadas).isEmpty();
  }

  @Test
  void factoryDaFonteNaoExigePrefixoHmlEDestinoContinuaExigindo() {
    R2StorageProperties properties = properties();
    properties.setPublicMediaPrefix("uploads/");
    properties.setPrivateMediaPrefix("privado/");
    properties.setDocumentPrefix("documentos/");

    assertThat(R2ObjectStorageFactory.criarFonteLegadaSomenteLeitura(properties)).isNotNull();
    assertThatThrownBy(() -> R2ObjectStorageFactory.criarParaMigracao(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("prefixo invalido");
  }

  private static Map<StorageArea, String> buckets() {
    return Map.of(
        StorageArea.PUBLIC_MEDIA, "public-source",
        StorageArea.PRIVATE_MEDIA, "private-source",
        StorageArea.PRIVATE_DOCUMENT, "document-source");
  }

  private static R2StorageProperties properties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEndpoint("http://127.0.0.1:9000");
    properties.setAccessKey("identificador-sintetico");
    properties.setSigningValue("valor-sintetico-nao-secreto");
    properties.setPublicMediaBucket("public-source");
    properties.setPrivateMediaBucket("private-source");
    properties.setDocumentBucket("document-source");
    return properties;
  }

  private static final class RecordingOperations implements R2Operations {

    private final List<String> chamadas = new ArrayList<>();

    @Override
    public void put(String bucket, String key, byte[] content, String contentType) {
      throw new AssertionError("fonte legada nao pode escrever");
    }

    @Override
    public ObjectWriteResult putIfAbsent(
        String bucket,
        String key,
        byte[] content,
        String contentType) {
      throw new AssertionError("fonte legada nao pode escrever");
    }

    @Override
    public boolean exists(String bucket, String key) {
      chamadas.add("HEAD:" + bucket + ":" + key);
      return true;
    }

    @Override
    public StoredObject get(String bucket, String key) {
      chamadas.add("GET:" + bucket + ":" + key);
      return new StoredObject(new byte[] {1, 2, 3}, "image/jpeg");
    }

    @Override
    public void delete(String bucket, String key) {
      throw new AssertionError("fonte legada nao pode remover");
    }

    @Override
    public URI presignGet(String bucket, String key, Duration ttl) {
      throw new AssertionError("fonte legada nao pode gerar URL");
    }
  }
}
