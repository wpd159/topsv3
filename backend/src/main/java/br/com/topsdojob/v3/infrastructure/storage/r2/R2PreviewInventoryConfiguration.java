package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorageInventory;
import br.com.topsdojob.v3.infrastructure.storage.StorageArea;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(R2StorageProperties.class)
public class R2PreviewInventoryConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
  ObjectStorageInventory restrictedMediaPreviewInventory(R2StorageProperties properties) {
    properties.validatePublicListingConfigured();
    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    R2Operations operations = new R2SigV4Client(
        httpClient,
        URI.create(properties.getEndpoint()),
        properties.getRegion(),
        properties.getAccessKey(),
        properties.getSigningValue());
    return (area, prefix, continuationToken, maxKeys) -> {
      validarListagemPublica(properties, area, prefix, maxKeys);
      return operations.list(
          properties.getPublicMediaBucket(),
          prefix,
          continuationToken,
          maxKeys);
    };
  }

  private void validarListagemPublica(
      R2StorageProperties properties,
      StorageArea area,
      String prefix,
      int maxKeys) {
    if (area != StorageArea.PUBLIC_MEDIA) {
      throw new IllegalArgumentException("Inventario restrito a midia publica");
    }
    if (prefix == null || prefix.isBlank() || prefix.startsWith("/")
        || prefix.contains("..") || prefix.contains("\\")
        || prefix.contains("?") || prefix.contains("#")
        || !prefix.startsWith(properties.getPublicMediaPrefix())) {
      throw new IllegalArgumentException("Prefixo fora da area publica autorizada");
    }
    if (maxKeys < 1 || maxKeys > 1_000) {
      throw new IllegalArgumentException("Tamanho da pagina fora do intervalo permitido");
    }
  }
}
