package br.com.topsdojob.v3.web.admin.conteudo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.conteudo.ConteudoSiteService;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminConteudoSiteRbacMethodSecurityTest.Config.class)
class AdminConteudoSiteRbacMethodSecurityTest {

  @Autowired
  private AdminConteudoSiteController controller;

  @Autowired
  private ConteudoSiteService service;

  @AfterEach
  void limparContexto() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void adminComAdminConfigurarAcessaCatalogo() {
    autenticar("ROLE_ADMIN", "ADMIN_CONFIGURAR");

    controller.listar();

    verify(service).listarAdministracao();
  }

  @Test
  void adminSemAdminConfigurarEhNegado() {
    autenticar("ROLE_ADMIN");

    assertThatThrownBy(controller::listar)
        .isInstanceOf(AuthorizationDeniedException.class);
  }

  @Test
  void moderadorNaoAcessaMesmoComAutoridadeIsolada() {
    autenticar("ROLE_MODERADOR", "ADMIN_CONFIGURAR");

    assertThatThrownBy(controller::listar)
        .isInstanceOf(AuthorizationDeniedException.class);
  }

  private void autenticar(String... authorities) {
    var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("admin", "n/a", granted));
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    ConteudoSiteService service() {
      return mock(ConteudoSiteService.class);
    }

    @Bean
    AdminConteudoSiteController controller(ConteudoSiteService service) {
      return new AdminConteudoSiteController(service);
    }
  }
}
