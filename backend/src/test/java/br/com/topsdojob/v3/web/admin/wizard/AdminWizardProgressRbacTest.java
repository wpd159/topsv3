package br.com.topsdojob.v3.web.admin.wizard;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.wizard.AdminWizardProgressService;
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
@ContextConfiguration(classes = AdminWizardProgressRbacTest.Config.class)
class AdminWizardProgressRbacTest {

  @Autowired
  private AdminWizardProgressController controller;

  @Autowired
  private AdminWizardProgressService service;

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void adminEModeradorComAnuncioLerPodemConsultar() {
    authenticate("ROLE_ADMIN", "ANUNCIO_LER");
    callDashboard();
    authenticate("ROLE_MODERADOR", "ANUNCIO_LER");
    callDashboard();

    verify(service, org.mockito.Mockito.times(2)).consultar(
        anyString(), any(), any(), any(), anyString(), anyString(),
        any(), any(), anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void usuarioComumEAdminSemPermissaoRecebemNegacao() {
    authenticate("ROLE_USUARIO", "ANUNCIO_LER");
    assertThatThrownBy(this::callDashboard)
        .isInstanceOf(AuthorizationDeniedException.class);

    authenticate("ROLE_ADMIN");
    assertThatThrownBy(this::callDashboard)
        .isInstanceOf(AuthorizationDeniedException.class);
  }

  private void callDashboard() {
    controller.dashboard(
        "30_DIAS", null, null, null, "TODOS", "TODOS",
        null, null, "TODOS", "TODOS", 0, 20);
  }

  private void authenticate(String... authorities) {
    var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("teste", "n/a", granted));
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {

    @Bean
    AdminWizardProgressService service() {
      return mock(AdminWizardProgressService.class);
    }

    @Bean
    AdminWizardProgressController controller(AdminWizardProgressService service) {
      return new AdminWizardProgressController(service);
    }
  }
}
