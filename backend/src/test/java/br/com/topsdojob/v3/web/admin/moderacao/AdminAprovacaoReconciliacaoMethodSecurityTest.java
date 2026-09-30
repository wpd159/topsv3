package br.com.topsdojob.v3.web.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.topsdojob.v3.application.admin.moderacao.AdminAprovacaoReconciliacaoService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminAprovacaoReconciliacaoService.Resultado;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.Arrays;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminAprovacaoReconciliacaoMethodSecurityTest.Config.class)
class AdminAprovacaoReconciliacaoMethodSecurityTest {
  @Autowired private AdminAprovacaoReconciliacaoController controller;
  @Autowired private AdminAprovacaoReconciliacaoService service;

  @AfterEach
  void limparContexto() {
    SecurityContextHolder.clearContext();
    reset(service);
  }

  @Test
  void adminEModeradorComPermissaoPodemConsultarSemCache() {
    UUID anuncio = UUID.randomUUID(), operacao = UUID.randomUUID();
    AdminUserPrincipal actor = actor();
    Resultado resultado = new Resultado("INCONCLUSIVA", anuncio, operacao, null, 7);
    when(service.consultar(anuncio, operacao, 7, null, actor)).thenReturn(resultado);

    autenticar(actor, "ROLE_ADMIN", "ANUNCIO_MODERAR");
    var admin = controller.consultar(anuncio, operacao, 7, null, actor);
    assertThat(admin.getBody()).isEqualTo(resultado);
    assertThat(admin.getHeaders().getCacheControl()).contains("no-store");

    autenticar(actor, "ROLE_MODERADOR", "ANUNCIO_MODERAR");
    assertThat(controller.consultar(anuncio, operacao, 7, null, actor).getBody()).isEqualTo(resultado);
    verify(service, org.mockito.Mockito.times(2)).consultar(anuncio, operacao, 7, null, actor);
  }

  @Test
  void papelSemPermissaoOuUsuarioComPermissaoNaoAcessamEvidencia() {
    UUID anuncio = UUID.randomUUID(), operacao = UUID.randomUUID();
    AdminUserPrincipal actor = actor();
    for (String[] autoridades : List.of(
        new String[] {"ROLE_ADMIN"},
        new String[] {"ROLE_MODERADOR"},
        new String[] {"ROLE_USUARIO", "ANUNCIO_MODERAR"},
        new String[] {"ROLE_COMERCIAL", "ANUNCIO_MODERAR"})) {
      autenticar(actor, autoridades);
      assertThatThrownBy(() -> controller.consultar(anuncio, operacao, 7, null, actor))
          .isInstanceOf(AuthorizationDeniedException.class);
    }
    verifyNoInteractions(service);
  }

  private AdminUserPrincipal actor() {
    return new AdminUserPrincipal(UUID.randomUUID(), "Admin sintetico", "admin@example.invalid", "n/a",
        List.of(PapelUsuario.ADMIN), List.of(), List.of(), true);
  }

  private void autenticar(AdminUserPrincipal actor, String... authorities) {
    var grants = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, "n/a", grants));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Config {
    @Bean AdminAprovacaoReconciliacaoService service() {
      return mock(AdminAprovacaoReconciliacaoService.class);
    }

    @Bean AdminAprovacaoReconciliacaoController controller(AdminAprovacaoReconciliacaoService service) {
      return new AdminAprovacaoReconciliacaoController(service);
    }
  }
}
