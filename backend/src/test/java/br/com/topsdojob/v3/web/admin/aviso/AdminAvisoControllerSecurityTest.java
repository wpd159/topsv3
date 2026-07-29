package br.com.topsdojob.v3.web.admin.aviso;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.aviso.AvisoService;
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
    controllers = AdminAvisoController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminAvisoControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AvisoService service;

  @Test
  void leituraSegueRbacAtualEAnonimoRecebe401() throws Exception {
    for (PapelUsuario papel : List.of(PapelUsuario.ADMIN, PapelUsuario.MODERADOR)) {
      mockMvc.perform(get("/api/admin/avisos")
              .with(authentication(tokenFor(papel, "ADMIN_CONFIGURAR"))))
          .andExpect(status().isOk());
    }
    mockMvc.perform(get("/api/admin/avisos")
            .with(authentication(tokenFor(PapelUsuario.MODERADOR))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/avisos"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void somenteAdminMutaEPostExigeCsrf() throws Exception {
    String payload = """
        {
          "titulo": "Aviso QA",
          "descricao": "Mensagem segura para homologacao.",
          "localExibicao": "SITE",
          "frequenciaExibicao": "DIARIO",
          "permiteDispensar": true
        }
        """;
    mockMvc.perform(post("/api/admin/avisos")
            .with(authentication(tokenFor(PapelUsuario.ADMIN, "ADMIN_CONFIGURAR")))
            .with(csrf())
            .header("Idempotency-Key", "idempotency-aviso-security")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/admin/avisos")
            .with(authentication(tokenFor(PapelUsuario.ADMIN, "ADMIN_CONFIGURAR")))
            .header("Idempotency-Key", "idempotency-aviso-no-csrf")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/avisos")
            .with(authentication(tokenFor(PapelUsuario.MODERADOR, "ADMIN_CONFIGURAR")))
            .with(csrf())
            .header("Idempotency-Key", "idempotency-aviso-moderador")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken tokenFor(
      PapelUsuario papel, String... permissions) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    for (String permission : permissions) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }
    AdminUserPrincipal principal = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Operador QA",
        "operador@example.invalid",
        "hash",
        List.of(papel),
        authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(value -> !value.startsWith("ROLE_"))
            .map(value -> new AdminPermissionDto(value, value))
            .toList(),
        authorities,
        true);
    return new UsernamePasswordAuthenticationToken(
        principal, principal.getPassword(), authorities);
  }
}
