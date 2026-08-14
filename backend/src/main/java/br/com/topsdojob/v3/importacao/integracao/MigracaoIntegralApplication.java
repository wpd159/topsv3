package br.com.topsdojob.v3.importacao.integracao;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@ComponentScan(basePackages = "br.com.topsdojob.v3.importacao.integracao")
@Profile("migracao-integral")
@ConditionalOnProperty(
    prefix = "app.migracao.integral",
    name = "enabled",
    havingValue = "true")
@Import(MigracaoIntegralApplication.ExecucaoImportadoresConfiguration.class)
public class MigracaoIntegralApplication {

  public static void main(String[] args) {
    SpringApplication.run(MigracaoIntegralApplication.class, args);
  }

  @Configuration(proxyBeanMethods = false)
  @Profile("migracao-integral")
  @ConditionalOnProperty(
      prefix = "app.migracao.integral",
      name = "operacao",
      havingValue = "EXECUTAR")
  @ComponentScan(basePackages = {
      "br.com.topsdojob.v3.importacao.anuncio",
      "br.com.topsdojob.v3.importacao.comercial",
      "br.com.topsdojob.v3.importacao.conteudoseo",
      "br.com.topsdojob.v3.importacao.financeiro"
  })
  @Import(BlogConteudoValidator.class)
  static class ExecucaoImportadoresConfiguration {
  }
}
