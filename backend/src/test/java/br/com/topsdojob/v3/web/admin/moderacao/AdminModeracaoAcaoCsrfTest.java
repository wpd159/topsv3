package br.com.topsdojob.v3.web.admin.moderacao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLoteService;
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
        controllers = AdminModeracaoAcaoController.class,
        properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminModeracaoAcaoCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminModeracaoAcaoService service;

    @MockBean
    private AdminModeracaoFotosLoteService fotosLoteService;

    @Test
    void mutacoesSemCsrfSaoRecusadas() throws Exception {
        mockMvc.perform(post("/api/admin/midias/{id}/decidir", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"REPROVAR\",\"motivo\":\"teste\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/moderacao/revisoes/{id}/decidir", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"APROVAR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/anuncios/{id}/aprovar", UUID.randomUUID())
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("ANUNCIO_MODERAR"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/anuncios/{id}/midias/decisoes", UUID.randomUUID())
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("MIDIA_REVISAR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fotos\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminComAutoridadeECsrfChegaAoContratoCanonico() throws Exception {
        mockMvc.perform(post("/api/admin/midias/{id}/decidir", UUID.randomUUID())
                        .with(user("admin").authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("MIDIA_REVISAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"REPROVAR\",\"motivo\":\"teste\"}"))
                .andExpect(status().isOk());

        verify(service).decidirMidia(any(), any(), any(), any());

        mockMvc.perform(post("/api/admin/anuncios/{id}/aprovar", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(service).aprovarEPublicarAnuncio(any(), any(), any());

        mockMvc.perform(post("/api/admin/anuncios/{id}/midias/decisoes", UUID.randomUUID())
                        .with(user("moderador").authorities(
                                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                                new SimpleGrantedAuthority("MIDIA_REVISAR")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fotos\":[]}"))
                .andExpect(status().isOk());

        verify(fotosLoteService).decidir(any(), any(), any(), any());
    }

    @Test
    void adminSemAutoridadeEUsuarioComumRecebem403() throws Exception {
        mockMvc.perform(post("/api/admin/midias/{id}/decidir", UUID.randomUUID())
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"REPROVAR\",\"motivo\":\"teste\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/moderacao/revisoes/{id}/decidir", UUID.randomUUID())
                        .with(user("usuario").authorities(new SimpleGrantedAuthority("ROLE_USUARIO")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisao\":\"APROVAR\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/anuncios/{id}/aprovar", UUID.randomUUID())
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
