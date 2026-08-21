package br.com.topsdojob.v3.infrastructure.storage.r2;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class R2ObjectStorageRealWriteIT {

  @Test
  void gravaLeAssinaERemoveSomenteNoBucketEPrefixoIsolados() throws Exception {
    R2RealWriteTestGate.Decision decision = R2RealWriteTestGate.evaluate(System.getenv());
    Assumptions.assumeTrue(decision.enabled(), decision.reason());
    R2RealWriteTestGate.Configuration gate = decision.configuration();

    R2StorageProperties properties = propertiesFromEnvironment(gate);
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    ObjectStorage delegate = new R2ObjectStorage(
        properties,
        new R2SigV4Client(
            httpClient,
            URI.create(properties.getEndpoint()),
            properties.getRegion(),
            properties.getAccessKey(),
            properties.getSigningValue()));

    Map<StorageArea, String> keys = new EnumMap<>(StorageArea.class);
    keys.put(StorageArea.PUBLIC_MEDIA, properties.getPublicMediaPrefix() + "smoke.txt");
    keys.put(StorageArea.PRIVATE_MEDIA, properties.getPrivateMediaPrefix() + "smoke.txt");
    keys.put(StorageArea.PRIVATE_DOCUMENT, properties.getDocumentPrefix() + "smoke.txt");
    byte[] content = "topsv3-r2-test-sintetico".getBytes(StandardCharsets.UTF_8);

    try (R2RealWriteTestStorage storage = new R2RealWriteTestStorage(
        delegate, gate.prefix())) {
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
    }
  }

  private static R2StorageProperties propertiesFromEnvironment(
      R2RealWriteTestGate.Configuration gate) {
    R2StorageProperties properties = new R2StorageProperties();
    properties.setEnabled(true);
    properties.setEndpoint(required("R2_ENDPOINT"));
    properties.setRegion(environment("R2_REGION", "auto"));
    properties.setAccessKey(required("R2_ACCESS_KEY"));
    properties.setSigningValue(required("R2_SIGNING_VALUE"));
    properties.setPublicMediaBucket(gate.bucket());
    properties.setPrivateMediaBucket(gate.bucket());
    properties.setDocumentBucket(gate.bucket());
    properties.setPublicMediaPrefix(gate.prefix() + "public/");
    properties.setPrivateMediaPrefix(gate.prefix() + "private/");
    properties.setDocumentPrefix(gate.prefix() + "documents/");
    properties.setPublicBaseUrl("https://test.invalid");
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