package br.com.topsdojob.v3.web.admin.anuncio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AdminAnuncioAtualizacaoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAnuncioAtualizacaoCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAnuncioAtualizacaoService service;

    @Test
    void edicaoSemCsrfERecusada() throws Exception {
        mockMvc.perform(put("/api/admin/anuncios/{id}", UUID.randomUUID())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminComAutoridadeECsrfChegaAoContrato() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/admin/anuncios/{id}", id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(service).atualizar(eq(id), any(), isNull(), anyString());
    }

    @Test
    void moderadorNaoEditaMesmoComCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/anuncios/{id}", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR"));
    }
}
