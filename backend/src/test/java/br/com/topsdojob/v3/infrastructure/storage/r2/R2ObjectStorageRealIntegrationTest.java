package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "R2_IT_ENABLED", matches = "true")
class R2ObjectStorageRealIntegrationTest {

  @Test
  void gravaLeVerificaAssinaERemoveObjetosSinteticosNosBucketsHml() throws Exception {
    R2StorageProperties properties = propertiesFromEnvironment();
    properties.validateConfigured();
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    R2ObjectStorage storage = new R2ObjectStorage(
        properties,
        new R2SigV4Client(
            httpClient,
            URI.create(properties.getEndpoint()),
            properties.getRegion(),
            properties.getAccessKey(),
            properties.getSigningValue()));

    String suffix = UUID.randomUUID() + ".txt";
    Map<StorageArea, String> keys = new EnumMap<>(StorageArea.class);
    keys.put(StorageArea.PUBLIC_MEDIA, properties.getPublicMediaPrefix() + "smoke/" + suffix);
    keys.put(StorageArea.PRIVATE_MEDIA, properties.getPrivateMediaPrefix() + "smoke/" + suffix);
    keys.put(StorageArea.PRIVATE_DOCUMENT, properties.getDocumentPrefix() + "smoke/" + suffix);
    byte[] content = "topsv3-r2-hml-sintetico".getBytes(StandardCharsets.UTF_8);

    try {
      for (Map.Entry<StorageArea, String> entry : keys.entrySet()) {
        storage.put(entry.getKey(), entry.getValue(), content, "text/plain; charset=utf-8");
        assertThat(storage.exists(entry.getKey(), entry.getValue())).isTrue();
        assertThat(storage.get(entry.getKey(), entry.getValue()).content()).isEqualTo(content);
      }

      URI signedUrl = storage.temporaryGetUrl(
          StorageArea.PRIVATE_MEDIA,
          keys.get(StorageArea.PRIVATE_MEDIA),
          Duration.ofMinutes(2));
      HttpResponse<byte[]> signedResponse = httpClient.send(
          HttpRequest.newBuilder(signedUrl).timeout(Duration.ofSeconds(30)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
      assertThat(signedResponse.statusCode()).isEqualTo(200);
      assertThat(signedResponse.body()).isEqualTo(content);

      assertThat(storage.publicUrl(
          StorageArea.PRIVATE_MEDIA,
          keys.get(StorageArea.PRIVATE_MEDIA))).isEmpty();
      assertThat(storage.publicUrl(
          StorageArea.PRIVATE_DOCUMENT,
          keys.get(StorageArea.PRIVATE_DOCUMENT))).isEmpty();
      assertThatThrownBy(() -> storage.exists(StorageArea.PUBLIC_MEDIA, "producao/nao-permitido"))
          .isInstanceOf(IllegalArgumentException.class);
    } finally {
      for (Map.Entry<StorageArea, String> entry : keys.entrySet()) {
        storage.delete(entry.getKey(), entry.getValue());
        assertThat(storage.exists(entry.getKey(), entry.getValue())).isFalse();
      }
    }
  }

  private static R2StorageProperties propertiesFromEnvironment() {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint(required("R2_ENDPOINT"));
    properties.setRegion(environment("R2_REGION", "auto"));
    properties.setAccessKey(required("R2_ACCESS_KEY"));
    properties.setSigningValue(required("R2_SIGNING_VALUE"));
    properties.setPublicMediaBucket(required("R2_PUBLIC_MEDIA_BUCKET"));
    properties.setPrivateMediaBucket(required("R2_PRIVATE_MEDIA_BUCKET"));
    properties.setDocumentBucket(required("R2_DOCUMENT_BUCKET"));
    properties.setPublicMediaPrefix(environment(
        "R2_PUBLIC_MEDIA_PREFIX", "hml/midias-aprovadas/"));
    properties.setPrivateMediaPrefix(environment(
        "R2_PRIVATE_MEDIA_PREFIX", "hml/midias-pendentes/"));
    properties.setDocumentPrefix(environment("R2_DOCUMENT_PREFIX", "hml/documentos/"));
    properties.setPublicBaseUrl(System.getenv("R2_PUBLIC_BASE_URL"));
    properties.setSignedUrlTtlSeconds(300);
    return properties;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variavel obrigatoria ausente: " + name);
    }
    return value;
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
