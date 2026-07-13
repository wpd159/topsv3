package br.com.topsdojob.v3.infrastructure.storage.r2;

import br.com.topsdojob.v3.infrastructure.storage.ObjectStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(R2StorageProperties.class)
public class R2StorageConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
  ObjectStorage r2ObjectStorage(R2StorageProperties properties) {
    properties.validateConfigured();
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
    return new R2ObjectStorage(properties, operations);
  }
}
