package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import org.junit.jupiter.api.Test;

class R2PreviewInventoryConfigurationTest {

  @Test
  void validacaoDeListagemPublicaNaoExigeBucketsPrivados() {
    R2StorageProperties properties = publicProperties();

    assertThatCode(properties::validatePublicListingConfigured).doesNotThrowAnyException();
    assertThatThrownBy(properties::validateConfigured)
        .hasMessageContaining("private-media-bucket");
  }

  @Test
  void inventarioRecusaAreasPrivadasAntesDeQualquerRequest() {
    var inventory = new R2PreviewInventoryConfiguration()
        .restrictedMediaPreviewInventory(publicProperties());

    assertThatThrownBy(() -> inventory.list(
        StorageArea.PRIVATE_MEDIA,
        "hml/midias-aprovadas/restritas-borradas/v1/",
        null,
        1_000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("midia publica");
  }

  @Test
  void inventarioRecusaPrefixoForaDaAreaEpaginaInvalidaAntesDoRequest() {
    var inventory = new R2PreviewInventoryConfiguration()
        .restrictedMediaPreviewInventory(publicProperties());

    assertThatThrownBy(() -> inventory.list(
        StorageArea.PUBLIC_MEDIA,
        "outro-prefixo/",
        null,
        1_000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Prefixo");
    assertThatThrownBy(() -> inventory.list(
        StorageArea.PUBLIC_MEDIA,
        "hml/midias-aprovadas/restritas-borradas/v1/",
        null,
        1_001))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pagina");
  }

  private R2StorageProperties publicProperties() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint("https://example.invalid");
    properties.setRegion("auto");
    properties.setAccessKey("test-access-key");
    properties.setSigningValue("test-signing-value");
    properties.setPublicMediaBucket("test-public-bucket");
    properties.setPublicMediaPrefix("hml/midias-aprovadas/");
    return properties;
  }
}
