package br.com.topsdojob.v3.web.admin.conteudo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.conteudo.AdminCategoriaHomeService;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AdminCategoriaHomeRbacMethodSecurityTest.Config.class)
class AdminCategoriaHomeRbacMethodSecurityTest {

    @Autowired
    private AdminCategoriaHomeController controller;

    @Autowired
    private AdminCategoriaHomeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void configurarHttp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminComAdminConfigurarAcessaListagemETaxonomia() throws Exception {
        autenticar("ROLE_ADMIN", "ADMIN_CONFIGURAR");

        mockMvc.perform(get("/api/admin/categorias-home"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/categorias-home/categorias-canonicas"))
                .andExpect(status().isOk());

        verify(service).listar();
        verify(service).listarCategoriasCanonicas();
    }

    @Test
    void adminSemAdminConfigurarEhNegado() {
        autenticar("ROLE_ADMIN");

        assertThatThrownBy(controller::listar)
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    void usuarioComumNaoAcessaMesmoSeReceberAutoridadeIsolada() {
        autenticar("ROLE_USUARIO", "ADMIN_CONFIGURAR");

        assertThatThrownBy(controller::listarCategoriasCanonicas)
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private void autenticar(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin.stories.hml", "n/a", granted));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        AdminCategoriaHomeService service() {
            return mock(AdminCategoriaHomeService.class);
        }

        @Bean
        AdminCategoriaHomeController controller(AdminCategoriaHomeService service) {
            return new AdminCategoriaHomeController(service);
        }
    }
}
