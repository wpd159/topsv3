package br.com.topsdojob.v3.web.admin.moderacao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
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
@ContextConfiguration(classes = AdminMidiaRbacMethodSecurityTest.Config.class)
class AdminMidiaRbacMethodSecurityTest {

    @Autowired
    private AdminModeracaoAcaoController controller;

    @Autowired
    private AdminModeracaoAcaoService service;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminComMidiaRevisarPodeExecutarRevisao() {
        autenticar("ROLE_ADMIN", "MIDIA_REVISAR");

        controller.decidirMidia(UUID.randomUUID(), null, null, new MockHttpServletRequest());

        verify(service).decidirMidia(any(), any(), any(), any());
    }

    @Test
    void usuarioComumSemMidiaRevisarEhNegado() {
        autenticar("ROLE_USUARIO");

        assertThatThrownBy(() -> controller.decidirMidia(
                UUID.randomUUID(), null, null, new MockHttpServletRequest()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void perfilAdministrativoSemMidiaRevisarEhNegado() {
        autenticar("ROLE_ADMIN");

        assertThatThrownBy(() -> controller.decidirMidia(
                UUID.randomUUID(), null, null, new MockHttpServletRequest()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private void autenticar(String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teste", "n/a", granted));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AdminModeracaoAcaoService service() {
            return mock(AdminModeracaoAcaoService.class);
        }

        @Bean
        AdminModeracaoAcaoController controller(AdminModeracaoAcaoService service) {
            return new AdminModeracaoAcaoController(service);
        }
    }
}
