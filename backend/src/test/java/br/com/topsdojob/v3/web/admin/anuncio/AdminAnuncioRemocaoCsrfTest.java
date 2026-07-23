package br.com.topsdojob.v3.web.admin.anuncio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioRemocaoService;
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
    controllers = AdminAnuncioRemocaoController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAnuncioRemocaoCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AdminAnuncioRemocaoService service;

  @Test
  void csrfAusenteERecusado() throws Exception {
    mockMvc.perform(post("/api/admin/anuncios/{id}/remocao-logica", UUID.randomUUID())
            .with(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(remocaoJson()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminComAutoridadeECsrfExecutaRemocao() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/api/admin/anuncios/{id}/remocao-logica", id)
            .with(admin())
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(remocaoJson()))
        .andExpect(status().isOk());

    verify(service).remover(eq(id), any(), any(), anyString());
  }

  @Test
  void moderadorEUsuarioRecebem403() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/api/admin/anuncios/{id}/remocao-logica", id)
            .with(user("moderador").authorities(
                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(remocaoJson()))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/anuncios/{id}/remocao-logica", id)
            .with(user("usuario").authorities(
                new SimpleGrantedAuthority("ROLE_USUARIO"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(remocaoJson()))
        .andExpect(status().isForbidden());
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
    return user("admin").authorities(
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("ANUNCIO_MODERAR"));
  }

  private String remocaoJson() {
    return """
        {
          "motivo": "remocao administrativa confirmada"
        }
        """;
  }
}
