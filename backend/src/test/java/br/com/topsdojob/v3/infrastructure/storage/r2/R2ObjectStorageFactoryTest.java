package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class R2ObjectStorageFactoryTest {

  @Test
  void permiteHttpSomenteParaMinioEmLoopbackNaMigracao() {
    R2StorageProperties local = properties("http://127.0.0.1:9000");

    assertThat(R2ObjectStorageFactory.criarParaMigracao(local)).isNotNull();
    assertThatThrownBy(() -> R2ObjectStorageFactory.criarParaMigracao(
        properties("http://minio.example.invalid")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loopback");
  }

  private static R2StorageProperties properties(String endpoint) {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEndpoint(endpoint);
    properties.setAccessKey("identificador-sintetico");
    properties.setSigningValue("valor-sintetico-nao-secreto");
    properties.setPublicMediaBucket("publico-sintetico");
    properties.setPrivateMediaBucket("privado-sintetico");
    properties.setDocumentBucket("documento-sintetico");
    properties.setPublicMediaPrefix("hml/publico/");
    properties.setPrivateMediaPrefix("hml/privado/");
    properties.setDocumentPrefix("hml/documentos/");
    return properties;
  }
}
