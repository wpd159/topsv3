package br.com.topsdojob.v3.web.admin.stories;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.stories.AdminStoriesGestaoService;
import br.com.topsdojob.v3.application.publico.anunciante.StoryEncerramentoService;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AdminStoriesGestaoController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminStoriesGestaoControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AdminStoriesGestaoService gestaoService;

  @MockBean
  private StoryEncerramentoService encerramentoService;

  @Test
  void somenteAdminConsultaERespostaNaoPodeSerCacheada() throws Exception {
    mockMvc.perform(get("/api/admin/stories/gestao")
            .with(authentication(tokenFor(PapelUsuario.ADMIN))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(get("/api/admin/stories/gestao")
            .with(authentication(tokenFor(PapelUsuario.MODERADOR))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/stories/gestao"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void remocaoExigeAdminECsrf() throws Exception {
    UUID storyId = UUID.randomUUID();
    String payload = """
        {
          "motivo": "VIOLACAO_REGRAS",
          "descricao": "Remocao administrativa QA"
        }
        """;
    mockMvc.perform(post("/api/admin/stories/{id}/remover", storyId)
            .with(authentication(tokenFor(PapelUsuario.ADMIN)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(post("/api/admin/stories/{id}/remover", storyId)
            .with(authentication(tokenFor(PapelUsuario.ADMIN)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/stories/{id}/remover", storyId)
            .with(authentication(tokenFor(PapelUsuario.MODERADOR)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken tokenFor(PapelUsuario papel) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    AdminUserPrincipal principal = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Operador QA",
        "operador@example.invalid",
        "hash",
        List.of(papel),
        List.<AdminPermissionDto>of(),
        authorities,
        true);
    return new UsernamePasswordAuthenticationToken(
        principal, principal.getPassword(), authorities);
  }
}
