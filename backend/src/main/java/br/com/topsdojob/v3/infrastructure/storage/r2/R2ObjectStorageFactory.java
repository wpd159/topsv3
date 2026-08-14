package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

public final class R2ObjectStorageFactory {

  private R2ObjectStorageFactory() {
  }

  public static ObjectStorage criar(R2StorageProperties properties) {
    properties.validateConfigured();
    return criarSemValidar(properties);
  }

  public static ObjectStorage criarParaMigracao(R2StorageProperties properties) {
    validarConfiguracaoMigracao(properties);
    return criarSemValidar(properties);
  }

  public static FonteMidiaMigracao criarFonteLegadaSomenteLeitura(
      R2StorageProperties properties) {
    validarConfiguracaoFonteLegada(properties);
    HttpClient httpClient = httpClient();
    R2Operations operations = new R2SigV4Client(
        httpClient,
        URI.create(properties.getEndpoint()),
        properties.getRegion(),
        properties.getAccessKey(),
        properties.getSigningValue());
    return new FonteR2LegadaSomenteLeitura(
        operations,
        Map.of(
            StorageArea.PUBLIC_MEDIA, properties.getPublicMediaBucket(),
            StorageArea.PRIVATE_MEDIA, properties.getPrivateMediaBucket(),
            StorageArea.PRIVATE_DOCUMENT, properties.getDocumentBucket()));
  }

  private static ObjectStorage criarSemValidar(R2StorageProperties properties) {
    HttpClient httpClient = httpClient();
    R2Operations operations = new R2SigV4Client(
        httpClient,
        URI.create(properties.getEndpoint()),
        properties.getRegion(),
        properties.getAccessKey(),
        properties.getSigningValue());
    return new R2ObjectStorage(properties, operations);
  }

  private static void validarConfiguracaoMigracao(R2StorageProperties properties) {
    exigir(properties.getEndpoint(), "endpoint");
    exigir(properties.getAccessKey(), "access-key");
    exigir(properties.getSigningValue(), "signing-value");
    exigir(properties.getPublicMediaBucket(), "public-media-bucket");
    exigir(properties.getPrivateMediaBucket(), "private-media-bucket");
    exigir(properties.getDocumentBucket(), "document-bucket");
    validarPrefixo(properties.getPublicMediaPrefix(), "public-media-prefix");
    validarPrefixo(properties.getPrivateMediaPrefix(), "private-media-prefix");
    validarPrefixo(properties.getDocumentPrefix(), "document-prefix");
    validarEndpoint(properties.getEndpoint());
  }

  private static void validarConfiguracaoFonteLegada(R2StorageProperties properties) {
    exigir(properties.getEndpoint(), "endpoint");
    exigir(properties.getAccessKey(), "access-key");
    exigir(properties.getSigningValue(), "signing-value");
    exigir(properties.getPublicMediaBucket(), "public-media-bucket");
    exigir(properties.getPrivateMediaBucket(), "private-media-bucket");
    exigir(properties.getDocumentBucket(), "document-bucket");
    validarEndpoint(properties.getEndpoint());
  }

  private static void validarEndpoint(String valor) {
    URI endpoint = URI.create(valor);
    boolean https = "https".equalsIgnoreCase(endpoint.getScheme());
    boolean loopbackHttp = "http".equalsIgnoreCase(endpoint.getScheme())
        && endpoint.getHost() != null
        && ("localhost".equalsIgnoreCase(endpoint.getHost())
            || "127.0.0.1".equals(endpoint.getHost())
            || "::1".equals(endpoint.getHost()));
    if ((!https && !loopbackHttp)
        || endpoint.getHost() == null
        || endpoint.getUserInfo() != null
        || endpoint.getQuery() != null
        || endpoint.getFragment() != null) {
      throw new IllegalStateException(
          "storage da migracao exige HTTPS ou HTTP estritamente em loopback");
    }
  }

  private static HttpClient httpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  private static void validarPrefixo(String valor, String campo) {
    exigir(valor, campo);
    if (!valor.startsWith("hml/") || !valor.endsWith("/") || valor.contains("..")) {
      throw new IllegalStateException("prefixo invalido no storage da migracao: " + campo);
    }
  }

  private static void exigir(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException("storage da migracao sem " + campo);
    }
  }
}
