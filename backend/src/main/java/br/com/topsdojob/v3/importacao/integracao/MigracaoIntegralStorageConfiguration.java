package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.importacao.midia.FonteMidiaMigracao;
import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2ObjectStorageFactory;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2StorageProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties({
    MigracaoIntegralStorageConfiguration.FonteProperties.class,
    MigracaoIntegralStorageConfiguration.DestinoProperties.class
})
public class MigracaoIntegralStorageConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "app.migracao.integral",
      name = "operacao",
      havingValue = "PRODUZIR_MANIFESTO")
  FonteMidiaMigracao migracaoIntegralFonteSomenteLeitura(FonteProperties fonteProperties) {
    return R2ObjectStorageFactory.criarFonteLegadaSomenteLeitura(fonteProperties.r2());
  }

  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "app.migracao.integral",
      name = "operacao",
      havingValue = "EXECUTAR")
  ObjectStorage migracaoIntegralDestinoObjectStorage(DestinoProperties destinoProperties) {
    return R2ObjectStorageFactory.criarParaMigracao(destinoProperties.r2());
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.migracao.integral",
      name = "operacao",
      havingValue = "EXECUTAR")
  ArmazenamentosMigracaoIntegral armazenamentosMigracaoIntegral(
      FonteProperties fonteProperties,
      DestinoProperties destinoProperties,
      ObjectStorage migracaoIntegralDestinoObjectStorage) {
    return new ArmazenamentosMigracaoIntegral(
        R2ObjectStorageFactory.criarFonteLegadaSomenteLeitura(fonteProperties.r2()),
        migracaoIntegralDestinoObjectStorage,
        Map.of(
            StorageArea.PUBLIC_MEDIA, destinoProperties.getPublicMediaBucket(),
            StorageArea.PRIVATE_MEDIA, destinoProperties.getPrivateMediaBucket(),
            StorageArea.PRIVATE_DOCUMENT, destinoProperties.getDocumentBucket()));
  }

  @ConfigurationProperties(prefix = "app.migracao.integral.storage.fonte")
  public static class FonteProperties extends MigracaoStorageProperties {
  }

  @ConfigurationProperties(prefix = "app.migracao.integral.storage.destino")
  public static class DestinoProperties extends MigracaoStorageProperties {
  }

  public abstract static class MigracaoStorageProperties {

    private final R2StorageProperties delegate = new R2StorageProperties();

    R2StorageProperties r2() {
      return delegate;
    }

    public boolean isEnabled() {
      return delegate.isEnabled();
    }

    public void setEnabled(boolean enabled) {
      delegate.setEnabled(enabled);
    }

    public String getEndpoint() {
      return delegate.getEndpoint();
    }

    public void setEndpoint(String endpoint) {
      delegate.setEndpoint(endpoint);
    }

    public String getRegion() {
      return delegate.getRegion();
    }

    public void setRegion(String region) {
      delegate.setRegion(region);
    }

    public String getAccessKey() {
      return delegate.getAccessKey();
    }

    public void setAccessKey(String accessKey) {
      delegate.setAccessKey(accessKey);
    }

    public String getSigningValue() {
      return delegate.getSigningValue();
    }

    public void setSigningValue(String signingValue) {
      delegate.setSigningValue(signingValue);
    }

    public String getPublicMediaBucket() {
      return delegate.getPublicMediaBucket();
    }

    public void setPublicMediaBucket(String publicMediaBucket) {
      delegate.setPublicMediaBucket(publicMediaBucket);
    }

    public String getPrivateMediaBucket() {
      return delegate.getPrivateMediaBucket();
    }

    public void setPrivateMediaBucket(String privateMediaBucket) {
      delegate.setPrivateMediaBucket(privateMediaBucket);
    }

    public String getDocumentBucket() {
      return delegate.getDocumentBucket();
    }

    public void setDocumentBucket(String documentBucket) {
      delegate.setDocumentBucket(documentBucket);
    }

    public String getPublicMediaPrefix() {
      return delegate.getPublicMediaPrefix();
    }

    public void setPublicMediaPrefix(String publicMediaPrefix) {
      delegate.setPublicMediaPrefix(publicMediaPrefix);
    }

    public String getPrivateMediaPrefix() {
      return delegate.getPrivateMediaPrefix();
    }

    public void setPrivateMediaPrefix(String privateMediaPrefix) {
      delegate.setPrivateMediaPrefix(privateMediaPrefix);
    }

    public String getDocumentPrefix() {
      return delegate.getDocumentPrefix();
    }

    public void setDocumentPrefix(String documentPrefix) {
      delegate.setDocumentPrefix(documentPrefix);
    }

    public String getPublicBaseUrl() {
      return delegate.getPublicBaseUrl();
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
      delegate.setPublicBaseUrl(publicBaseUrl);
    }

    public String getPreservedPublicMediaBucket() {
      return delegate.getPreservedPublicMediaBucket();
    }

    public void setPreservedPublicMediaBucket(String preservedPublicMediaBucket) {
      delegate.setPreservedPublicMediaBucket(preservedPublicMediaBucket);
    }

    public String getPreservedPublicMediaPrefix() {
      return delegate.getPreservedPublicMediaPrefix();
    }

    public void setPreservedPublicMediaPrefix(String preservedPublicMediaPrefix) {
      delegate.setPreservedPublicMediaPrefix(preservedPublicMediaPrefix);
    }

    public String getPreservedPublicBaseUrl() {
      return delegate.getPreservedPublicBaseUrl();
    }

    public void setPreservedPublicBaseUrl(String preservedPublicBaseUrl) {
      delegate.setPreservedPublicBaseUrl(preservedPublicBaseUrl);
    }

    public long getSignedUrlTtlSeconds() {
      return delegate.getSignedUrlTtlSeconds();
    }

    public void setSignedUrlTtlSeconds(long signedUrlTtlSeconds) {
      delegate.setSignedUrlTtlSeconds(signedUrlTtlSeconds);
    }
  }
}
