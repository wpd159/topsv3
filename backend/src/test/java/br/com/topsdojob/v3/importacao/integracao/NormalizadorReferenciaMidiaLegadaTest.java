package br.com.topsdojob.v3.importacao.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.importacao.integracao.MigracaoIntegralStorageConfiguration.FonteProperties;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import org.junit.jupiter.api.Test;

class NormalizadorReferenciaMidiaLegadaTest {

  private final NormalizadorReferenciaMidiaLegada normalizador =
      new NormalizadorReferenciaMidiaLegada(properties().r2());

  @Test
  void aceitaChaveLegadaForaDeHmlSomenteComoOrigem() {
    var resultado = normalizador.normalizar(
        "r2://public-source/legacy/media/foto.jpg",
        StorageArea.PUBLIC_MEDIA);

    assertThat(resultado.valida()).isTrue();
    assertThat(resultado.origem().area()).isEqualTo(StorageArea.PUBLIC_MEDIA);
    assertThat(resultado.origem().localizador()).isEqualTo("legacy/media/foto.jpg");
    assertThat(resultado.extensao()).isEqualTo("jpg");
  }

  @Test
  void converteUrlDaOrigemExataSemManterUrlNoManifesto() {
    var resultado = normalizador.normalizar(
        "https://media.example.test/base/anuncios/foto%20qa.png",
        StorageArea.PUBLIC_MEDIA);

    assertThat(resultado.valida()).isTrue();
    assertThat(resultado.origem().localizador()).isEqualTo("anuncios/foto qa.png");
    assertThat(resultado.origem().localizador()).doesNotContain("://");
  }

  @Test
  void rejeitaQueryFragmentoTraversalUrlExternaEBucketInjection() {
    assertThat(normalizador.normalizar(
        "https://media.example.test/base/foto.jpg?parametro=teste",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
    assertThat(normalizador.normalizar(
        "https://media.example.test/base/foto.jpg#fragmento",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
    assertThat(normalizador.normalizar(
        "https://media.example.test/base/%2e%2e/segredo.jpg",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
    assertThat(normalizador.normalizar(
        "https://outra.example.test/base/foto.jpg",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
    assertThat(normalizador.normalizar(
        "public-source/legacy/foto.jpg",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
  }

  @Test
  void rejeitaBucketR2DesconhecidoEBarraInvertida() {
    assertThat(normalizador.normalizar(
        "r2://bucket-nao-configurado/legacy/foto.jpg",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
    assertThat(normalizador.normalizar(
        "legacy\\foto.jpg",
        StorageArea.PUBLIC_MEDIA).valida()).isFalse();
  }

  @Test
  void bucketPrivadoCompartilhadoUsaAreaPersistidaSemPermitirAmbiguidade() {
    FonteProperties properties = properties();
    properties.setPrivateMediaBucket("private-shared");
    properties.setDocumentBucket("private-shared");
    NormalizadorReferenciaMidiaLegada compartilhado =
        new NormalizadorReferenciaMidiaLegada(properties.r2());

    assertThat(compartilhado.normalizar(
        "r2://private-shared/legacy/media.bin",
        StorageArea.PRIVATE_MEDIA).origem().area()).isEqualTo(StorageArea.PRIVATE_MEDIA);
    assertThat(compartilhado.normalizar(
        "r2://private-shared/legacy/documento.pdf",
        StorageArea.PRIVATE_DOCUMENT).origem().area()).isEqualTo(StorageArea.PRIVATE_DOCUMENT);
    assertThat(compartilhado.normalizar(
        "r2://private-shared/legacy/sem-area.bin",
        null).valida()).isFalse();
  }

  private static FonteProperties properties() {
    FonteProperties properties = new FonteProperties();
    properties.setPublicMediaBucket("public-source");
    properties.setPrivateMediaBucket("private-source");
    properties.setDocumentBucket("document-source");
    properties.setPublicBaseUrl("https://media.example.test/base");
    return properties;
  }
}
