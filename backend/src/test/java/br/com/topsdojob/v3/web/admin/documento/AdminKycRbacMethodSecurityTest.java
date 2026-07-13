package br.com.topsdojob.v3.web.admin.documento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import java.util.List;
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
@ContextConfiguration(classes = AdminKycRbacMethodSecurityTest.Config.class)
class AdminKycRbacMethodSecurityTest {

  @Autowired
  private AdminKycController controller;

  @Autowired
  private AdminKycService service;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void adminComDocumentoRevisarPodeConsultarFila() {
    authenticate("ROLE_ADMIN", "DOCUMENTO_REVISAR");

    controller.listarPendentes();

    verify(service).listarPendentes();
  }

  @Test
  void usuarioComumEAdminSemPermissaoSaoNegados() {
    authenticate("ROLE_USUARIO");
    assertThatThrownBy(controller::listarPendentes).isInstanceOf(AuthorizationDeniedException.class);

    authenticate("ROLE_ADMIN");
    assertThatThrownBy(controller::listarPendentes).isInstanceOf(AuthorizationDeniedException.class);
  }

  private void authenticate(String... authorities) {
    var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("teste", "n/a", granted));
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean
    AdminKycService service() {
      return mock(AdminKycService.class);
    }

    @Bean
    AdminKycController controller(AdminKycService service) {
      return new AdminKycController(service);
    }
  }
}
