package br.com.topsdojob.v3.web.admin.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceVisitorService;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminComplianceDocumentoSecurityTest.Config.class)
class AdminComplianceDocumentoSecurityTest {
  @Autowired AdminComplianceVisitorController controller;
  @Autowired AdminComplianceVisitorService service;

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
    reset(service);
  }

  @Test
  void adminComPermissaoRecebeBytesPrivadosSemCacheENosniff() {
    autenticar("ROLE_ADMIN", "SEGURANCA_GERENCIAR");
    UUID id = UUID.randomUUID();
    AdminUserPrincipal ator = mock(AdminUserPrincipal.class);
    when(service.carregarDocumento(eq(id), eq(ator), any()))
        .thenReturn(new AdminComplianceVisitorService.DocumentoPrivado(new byte[] {1, 2}, "application/pdf"));

    var response = controller.arquivo(id, ator, new MockHttpServletRequest());

    assertThat(response.getBody()).containsExactly(1, 2);
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
    assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
  }

  @Test
  void rbacRecusaAntesDaLeituraDoDocumento() {
    for (List<String> authorities : List.of(List.of("ROLE_ADMIN"),
        List.of("ROLE_MODERADOR", "SEGURANCA_GERENCIAR"), List.of("ROLE_USUARIO"))) {
      autenticar(authorities.toArray(String[]::new));
      assertThatThrownBy(() -> controller.arquivo(UUID.randomUUID(),
          mock(AdminUserPrincipal.class), new MockHttpServletRequest()))
          .isInstanceOf(AuthorizationDeniedException.class);
    }
    // A negativa RBAC ocorre antes do servico; nao e um acesso documental autorizado.
    verifyNoInteractions(service);
  }

  private void autenticar(String... authorities) {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        "sintetico", "n/a", List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
  }

  @Configuration
  @EnableMethodSecurity
  static class Config {
    @Bean AdminComplianceVisitorService service() { return mock(AdminComplianceVisitorService.class); }
    @Bean AdminComplianceVisitorController controller(AdminComplianceVisitorService service) {
      return new AdminComplianceVisitorController(service);
    }
  }
}
