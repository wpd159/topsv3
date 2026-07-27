package br.com.topsdojob.v3.web.admin.documento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.AdminKycThumbnailProcessor;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.UUID;
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
import org.springframework.mock.web.MockHttpServletRequest;

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

    var response = controller.listarPendentes();

    verify(service).listarPendentes();
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
  }

  @Test
  void usuarioComumEAdminSemPermissaoSaoNegados() {
    authenticate("ROLE_USUARIO");
    assertThatThrownBy(controller::listarPendentes).isInstanceOf(AuthorizationDeniedException.class);

    authenticate("ROLE_ADMIN");
    assertThatThrownBy(controller::listarPendentes).isInstanceOf(AuthorizationDeniedException.class);
  }

  @Test
  void adminEModeradorComDocumentoRevisarRecebemMiniaturaProtegida() {
    UUID documentoId = UUID.randomUUID();
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    var thumbnail = new AdminKycThumbnailProcessor.Thumbnail(
        new byte[] {1, 2, 3},
        "image/jpeg",
        "\"etag\"");
    org.mockito.Mockito.when(service.miniatura(
        org.mockito.ArgumentMatchers.eq(documentoId),
        org.mockito.ArgumentMatchers.eq(ator),
        org.mockito.ArgumentMatchers.any()))
        .thenReturn(thumbnail);

    for (String role : List.of("ROLE_ADMIN", "ROLE_MODERADOR")) {
      authenticate(role, "DOCUMENTO_REVISAR");
      var response = controller.miniatura(documentoId, ator, new MockHttpServletRequest());
      assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(response.getHeaders().getCacheControl()).contains("no-store");
      assertThat(response.getBody()).containsExactly(1, 2, 3);
    }
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
