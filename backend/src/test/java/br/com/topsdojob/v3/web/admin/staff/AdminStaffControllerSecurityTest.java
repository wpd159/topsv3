package br.com.topsdojob.v3.web.admin.staff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Pagina;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffService;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = AdminStaffController.class, properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminStaffControllerSecurityTest {
  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AdminStaffService service;

  @Test
  void apenasAdminComAdminConfigurarConsultaStaff() throws Exception {
    when(service.listar(any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
        .thenReturn(new Pagina<>(List.of(), 0, 20, 0, 0));

    mockMvc.perform(get("/api/admin/staff").with(authentication(token(PapelUsuario.ADMIN, true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.itens").isArray());
    mockMvc.perform(get("/api/admin/staff").with(authentication(token(PapelUsuario.MODERADOR, false))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/admin/staff").with(user("usuario").roles("USUARIO")))
        .andExpect(status().isForbidden());
  }

  @Test
  void mutacoesExigemAdminPermissaoECsrf() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.criar(any(), any(), any())).thenReturn(detalhe(id));
    when(service.atualizar(any(), any(), any(), any())).thenReturn(detalhe(id));
    when(service.remover(any(), any(), any())).thenReturn(detalhe(id));

    mockMvc.perform(post("/api/admin/staff")
            .with(authentication(token(PapelUsuario.ADMIN, true)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"QA Staff\",\"email\":\"qa.staff@example.invalid\",\"papel\":\"MODERADOR\",\"ativo\":true}"))
        .andExpect(status().isOk());
    mockMvc.perform(patch("/api/admin/staff/{id}", id)
            .with(authentication(token(PapelUsuario.ADMIN, true)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"QA Staff\",\"papel\":\"MODERADOR\",\"ativo\":false,\"versao\":0}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(patch("/api/admin/staff/{id}", id)
            .with(authentication(token(PapelUsuario.MODERADOR, true)))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nome\":\"QA Staff\",\"papel\":\"ADMIN\",\"ativo\":true,\"versao\":0}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/staff/{id}", id)
            .with(authentication(token(PapelUsuario.ADMIN, true)))
            .with(csrf()))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/admin/staff/{id}", id)
            .with(authentication(token(PapelUsuario.ADMIN, true))))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/admin/staff/{id}", id)
            .with(authentication(token(PapelUsuario.MODERADOR, true)))
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken token(PapelUsuario papel, boolean configurar) {
    List<GrantedAuthority> authorities = new java.util.ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    if (configurar) authorities.add(new SimpleGrantedAuthority("ADMIN_CONFIGURAR"));
    var principal = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Operador QA",
        "operador.qa@example.invalid",
        "hash",
        List.of(papel),
        authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> !authority.startsWith("ROLE_"))
            .map(authority -> new AdminPermissionDto(authority, authority))
            .toList(),
        authorities,
        true);
    return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
  }

  private Detalhe detalhe(UUID id) {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-28T10:00:00Z");
    return new Detalhe(
        new Resumo(
            id,
            "QA Staff",
            "qa.staff@example.invalid",
            "MODERADOR",
            "Moderador",
            "ATIVO",
            "Ativo",
            true,
            true,
            now,
            now,
            0),
        List.of(),
        List.of());
  }
}
