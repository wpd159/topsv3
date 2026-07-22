package br.com.topsdojob.v3.web.admin.readonly;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import java.util.Arrays;
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
@ContextConfiguration(classes = AdminAnuncioModeracaoRbacMethodSecurityTest.Config.class)
class AdminAnuncioModeracaoRbacMethodSecurityTest {

    @Autowired
    private AdminAnuncioDetalhadoController controller;

    @Autowired
    private AdminAnuncioDetalhadoConsultaService service;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminComAnuncioLerAcessaDetalhe() {
        autenticar("ROLE_ADMIN", "ANUNCIO_LER");
        UUID id = UUID.randomUUID();

        controller.detalhar(id);

        verify(service).detalhar(id, false);
    }

    @Test
    void moderadorComAnuncioLerAcessaDetalhe() {
        autenticar("ROLE_MODERADOR", "ANUNCIO_LER");
        UUID id = UUID.randomUUID();

        controller.detalhar(id);

        verify(service).detalhar(id, false);
    }

    @Test
    void usuarioMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_USUARIO", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void anuncianteMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_ANUNCIANTE", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void comercialMesmoComAutoridadeNaoAcessaDetalhe() {
        autenticar("ROLE_COMERCIAL", "ANUNCIO_LER");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void papelAdministrativoSemPermissaoNaoAcessaDetalhe() {
        autenticar("ROLE_ADMIN");

        assertThatThrownBy(() -> controller.detalhar(UUID.randomUUID()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private void autenticar(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teste", "n/a", granted));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AdminAnuncioDetalhadoConsultaService service() {
            return mock(AdminAnuncioDetalhadoConsultaService.class);
        }

        @Bean
        AdminAnuncioDetalhadoController controller(AdminAnuncioDetalhadoConsultaService service) {
            return new AdminAnuncioDetalhadoController(service);
        }
    }
}
