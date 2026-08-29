package br.com.topsdojob.v3.application.operacional.midia.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaPreviewIdentity;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoRunner;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2PreviewInventoryConfiguration;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.ArquivoMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;

class RestrictedMediaPreviewBackfillBootstrapTest {

  @Test
  void selecionaSomenteMarcadorExplicito() {
    assertThat(RestrictedMediaPreviewBackfillBootstrap.requested(new String[] {
        RestrictedMediaPreviewBackfillBootstrap.BOOTSTRAP_ARGUMENT,
        "--app.restricted-media-preview-reconciliation.mode=PLAN"
    })).isTrue();
    assertThat(RestrictedMediaPreviewBackfillBootstrap.requested(new String[] {
        "--spring.main.web-application-type=none"
    })).isFalse();
    assertThat(RestrictedMediaPreviewBackfillBootstrap.requested(null)).isFalse();
  }

  @Test
  void bootstrapForcaAplicacaoNaoWeb() {
    assertThat(RestrictedMediaPreviewBackfillBootstrap.application().getWebApplicationType())
        .isEqualTo(WebApplicationType.NONE);
  }

  @Test
  void configuracaoImportaSomenteBeansOperacionaisNecessarios() {
    Import imports = RestrictedMediaPreviewBackfillConfiguration.class
        .getAnnotation(Import.class);

    assertThat(Arrays.asList(imports.value())).containsExactlyInAnyOrder(
        MidiaRestritaPreviewIdentity.class,
        MidiaRestritaRegularizacaoRunner.class,
        MidiaRestritaRegularizacaoService.class,
        PreviewRestritoBackfillJdbcRepository.class,
        R2PreviewInventoryConfiguration.class);
    assertThat(RestrictedMediaPreviewBackfillConfiguration.class
        .isAnnotationPresent(org.springframework.context.annotation.ComponentScan.class))
        .isFalse();
  }

  @Test
  void configuracaoDedicadaExigeMarcadorEPermaneceForaDoContextoWebNormal() {
    ConditionalOnProperty condition = RestrictedMediaPreviewBackfillConfiguration.class
        .getAnnotation(ConditionalOnProperty.class);

    assertThat(condition.name()).containsExactly("app.bootstrap");
    assertThat(condition.havingValue()).isEqualTo("restricted-media-preview-backfill");
    assertThat(condition.matchIfMissing()).isFalse();
  }

  @Test
  void filtroJpaPermiteSomenteOsDoisRepositoriesDoBackfill() throws Exception {
    ExcludedPreviewBackfillRepositoryFilter filter =
        new ExcludedPreviewBackfillRepositoryFilter();

    assertThat(filter.match(metadata(AnuncioMidiaRepository.class), null)).isFalse();
    assertThat(filter.match(metadata(ArquivoMidiaRepository.class), null)).isFalse();
    assertThat(filter.match(metadata(String.class), null)).isTrue();
  }

  private MetadataReader metadata(Class<?> type) {
    MetadataReader reader = mock(MetadataReader.class);
    ClassMetadata metadata = mock(ClassMetadata.class);
    when(reader.getClassMetadata()).thenReturn(metadata);
    when(metadata.getClassName()).thenReturn(type.getName());
    return reader;
  }
}
