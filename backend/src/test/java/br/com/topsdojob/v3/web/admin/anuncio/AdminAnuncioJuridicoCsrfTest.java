package br.com.topsdojob.v3.web.admin.anuncio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioJuridicoService;
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
    controllers = AdminAnuncioJuridicoController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAnuncioJuridicoCsrfTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AdminAnuncioJuridicoService service;

  @Test
  void bloqueioSemCsrfERecusado() throws Exception {
    mockMvc.perform(post("/api/admin/anuncios/{id}/bloqueio-juridico", UUID.randomUUID())
            .with(admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(bloqueioJson()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminComAutoridadeECsrfAcessaTodosOsComandos() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/api/admin/anuncios/{id}/reativar", id).with(admin()).with(csrf()))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/anuncios/{id}/bloqueio-juridico", id)
            .with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bloqueioJson()))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/anuncios/{id}/bloqueio-juridico/usuario", id)
            .with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(bloqueioJson()))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/anuncios/{id}/desbloqueio-juridico", id)
            .with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/admin/anuncios/{id}/desbloqueio-juridico/usuario", id)
            .with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());

    verify(service).bloquearAnuncio(eq(id), any(), any(), anyString());
    verify(service).bloquearAnuncioEUsuario(eq(id), any(), any(), anyString());
    verify(service).desbloquearAnuncio(eq(id), any(), any(), anyString());
    verify(service).desbloquearUsuario(eq(id), any(), any(), anyString());
  }

  @Test
  void moderadorEUsuarioNaoExecutamComandosMesmoComAutoridade() throws Exception {
    UUID id = UUID.randomUUID();

    mockMvc.perform(post("/api/admin/anuncios/{id}/bloqueio-juridico", id)
            .with(user("moderador").authorities(
                new SimpleGrantedAuthority("ROLE_MODERADOR"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(bloqueioJson()))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/anuncios/{id}/reativar", id)
            .with(user("usuario").authorities(
                new SimpleGrantedAuthority("ROLE_USUARIO"),
                new SimpleGrantedAuthority("ANUNCIO_MODERAR")))
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
    return user("admin").authorities(
        new SimpleGrantedAuthority("ROLE_ADMIN"),
        new SimpleGrantedAuthority("ANUNCIO_MODERAR"));
  }

  private String bloqueioJson() {
    return """
        {
          "categoria": "FRAUDE",
          "motivo": "evidencia juridica confirmada",
          "observacaoInterna": "nota sanitizada"
        }
        """;
  }
}
