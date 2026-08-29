package br.com.topsdojob.v3.infrastructure.storage.r2;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.r2")
public class R2StorageProperties {

  private boolean enabled;
  private String endpoint;
  private String region = "auto";
  private String accessKey;
  private String signingValue;
  private String publicMediaBucket;
  private String privateMediaBucket;
  private String documentBucket;
  private String publicMediaPrefix = "hml/midias-aprovadas/";
  private String privateMediaPrefix = "hml/midias-pendentes/";
  private String documentPrefix = "hml/documentos/";
  private String publicBaseUrl;
  private String preservedPublicMediaBucket;
  private String preservedPublicMediaPrefix;
  private String preservedPublicBaseUrl;
  private long signedUrlTtlSeconds = 300;

  public void validateConfigured() {
    validatePublicListingConfigured();
    require(privateMediaBucket, "private-media-bucket");
    require(documentBucket, "document-bucket");
    validatePrefix(privateMediaPrefix, "private-media-prefix");
    validatePrefix(documentPrefix, "document-prefix");
    validatePreservedPublicOrigin();
    if (signedUrlTtlSeconds < 1 || signedUrlTtlSeconds > Duration.ofDays(7).toSeconds()) {
      throw new IllegalStateException("app.storage.r2.signed-url-ttl-seconds fora do intervalo permitido");
    }
  }

  public void validatePublicListingConfigured() {
    require(endpoint, "endpoint");
    require(accessKey, "access-key");
    require(signingValue, "signing-value");
    require(publicMediaBucket, "public-media-bucket");
    validatePrefix(publicMediaPrefix, "public-media-prefix");
    URI parsedEndpoint = URI.create(endpoint);
    if (!"https".equalsIgnoreCase(parsedEndpoint.getScheme()) || parsedEndpoint.getHost() == null) {
      throw new IllegalStateException("app.storage.r2.endpoint deve usar HTTPS");
    }
  }

  private static void require(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("app.storage.r2." + field + " obrigatorio quando R2 esta habilitado");
    }
  }

  private static void validatePrefix(String value, String field) {
    require(value, field);
    if (!value.startsWith("hml/") || !value.endsWith("/") || value.contains("..")) {
      throw new IllegalStateException("app.storage.r2." + field + " deve permanecer sob hml/");
    }
  }

  private void validatePreservedPublicOrigin() {
    boolean configured = hasText(preservedPublicMediaBucket)
        || hasText(preservedPublicMediaPrefix)
        || hasText(preservedPublicBaseUrl);
    if (!configured) {
      return;
    }
    require(preservedPublicMediaBucket, "preserved-public-media-bucket");
    require(preservedPublicMediaPrefix, "preserved-public-media-prefix");
    require(preservedPublicBaseUrl, "preserved-public-base-url");
    if (preservedPublicMediaPrefix.startsWith("/")
        || !preservedPublicMediaPrefix.endsWith("/")
        || preservedPublicMediaPrefix.contains("..")
        || !preservedPublicMediaPrefix.matches("[A-Za-z0-9._/-]+")) {
      throw new IllegalStateException(
          "app.storage.r2.preserved-public-media-prefix invalido");
    }
    URI base = URI.create(preservedPublicBaseUrl);
    if (!"https".equalsIgnoreCase(base.getScheme())
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || !(base.getPath() == null || base.getPath().isBlank() || "/".equals(base.getPath()))) {
      throw new IllegalStateException(
          "app.storage.r2.preserved-public-base-url deve ser uma origem HTTPS");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSigningValue() {
    return signingValue;
  }

  public void setSigningValue(String signingValue) {
    this.signingValue = signingValue;
  }

  public String getPublicMediaBucket() {
    return publicMediaBucket;
  }

  public void setPublicMediaBucket(String publicMediaBucket) {
    this.publicMediaBucket = publicMediaBucket;
  }

  public String getPrivateMediaBucket() {
    return privateMediaBucket;
  }

  public void setPrivateMediaBucket(String privateMediaBucket) {
    this.privateMediaBucket = privateMediaBucket;
  }

  public String getDocumentBucket() {
    return documentBucket;
  }

  public void setDocumentBucket(String documentBucket) {
    this.documentBucket = documentBucket;
  }

  public String getPublicMediaPrefix() {
    return publicMediaPrefix;
  }

  public void setPublicMediaPrefix(String publicMediaPrefix) {
    this.publicMediaPrefix = publicMediaPrefix;
  }

  public String getPrivateMediaPrefix() {
    return privateMediaPrefix;
  }

  public void setPrivateMediaPrefix(String privateMediaPrefix) {
    this.privateMediaPrefix = privateMediaPrefix;
  }

  public String getDocumentPrefix() {
    return documentPrefix;
  }

  public void setDocumentPrefix(String documentPrefix) {
    this.documentPrefix = documentPrefix;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getPreservedPublicMediaBucket() {
    return preservedPublicMediaBucket;
  }

  public void setPreservedPublicMediaBucket(String preservedPublicMediaBucket) {
    this.preservedPublicMediaBucket = preservedPublicMediaBucket;
  }

  public String getPreservedPublicMediaPrefix() {
    return preservedPublicMediaPrefix;
  }

  public void setPreservedPublicMediaPrefix(String preservedPublicMediaPrefix) {
    this.preservedPublicMediaPrefix = preservedPublicMediaPrefix;
  }

  public String getPreservedPublicBaseUrl() {
    return preservedPublicBaseUrl;
  }

  public void setPreservedPublicBaseUrl(String preservedPublicBaseUrl) {
    this.preservedPublicBaseUrl = preservedPublicBaseUrl;
  }

  public long getSignedUrlTtlSeconds() {
    return signedUrlTtlSeconds;
  }

  public void setSignedUrlTtlSeconds(long signedUrlTtlSeconds) {
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
  }
}
