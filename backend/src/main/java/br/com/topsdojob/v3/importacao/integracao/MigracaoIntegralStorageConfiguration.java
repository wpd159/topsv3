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
  @Primary
  ObjectStorage migracaoIntegralDestinoObjectStorage(DestinoProperties destinoProperties) {
    return R2ObjectStorageFactory.criarParaMigracao(destinoProperties);
  }

  @Bean
  ArmazenamentosMigracaoIntegral armazenamentosMigracaoIntegral(
      FonteProperties fonteProperties,
      DestinoProperties destinoProperties,
      ObjectStorage migracaoIntegralDestinoObjectStorage) {
    ObjectStorage fonte = R2ObjectStorageFactory.criarParaMigracao(fonteProperties);
    return new ArmazenamentosMigracaoIntegral(
        FonteMidiaMigracao.objectStorage(fonte),
        migracaoIntegralDestinoObjectStorage,
        Map.of(
            StorageArea.PUBLIC_MEDIA, destinoProperties.getPublicMediaBucket(),
            StorageArea.PRIVATE_MEDIA, destinoProperties.getPrivateMediaBucket(),
            StorageArea.PRIVATE_DOCUMENT, destinoProperties.getDocumentBucket()));
  }

  @ConfigurationProperties(prefix = "app.migracao.integral.storage.fonte")
  public static class FonteProperties extends R2StorageProperties {
  }

  @ConfigurationProperties(prefix = "app.migracao.integral.storage.destino")
  public static class DestinoProperties extends R2StorageProperties {
  }
}
