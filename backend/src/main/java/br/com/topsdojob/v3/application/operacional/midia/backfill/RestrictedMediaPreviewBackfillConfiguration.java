package br.com.topsdojob.v3.application.operacional.midia.backfill;

import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaPreviewIdentity;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoRunner;
import br.com.topsdojob.v3.application.operacional.midia.MidiaRestritaRegularizacaoService;
import br.com.topsdojob.v3.infrastructure.storage.r2.R2PreviewInventoryConfiguration;
import br.com.topsdojob.v3.persistence.entity.midia.AnuncioMidiaEntity;
import br.com.topsdojob.v3.persistence.repository.AnuncioMidiaRepository;
import br.com.topsdojob.v3.persistence.repository.PreviewRestritoBackfillJdbcRepository;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "app.bootstrap",
    havingValue = "restricted-media-preview-backfill")
@ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    TransactionAutoConfiguration.class
})
@EnableTransactionManagement
@EntityScan(basePackageClasses = AnuncioMidiaEntity.class)
@EnableJpaRepositories(
    basePackageClasses = AnuncioMidiaRepository.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.CUSTOM,
        classes = ExcludedPreviewBackfillRepositoryFilter.class))
@Import({
    MidiaRestritaPreviewIdentity.class,
    MidiaRestritaRegularizacaoRunner.class,
    MidiaRestritaRegularizacaoService.class,
    PreviewRestritoBackfillJdbcRepository.class,
    R2PreviewInventoryConfiguration.class
})
public class RestrictedMediaPreviewBackfillConfiguration {
}
