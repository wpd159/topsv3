package br.com.topsdojob.v3.web.admin.staff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.staff.AdminArquivoExportadorService;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = AdminArquivoExportadorController.class, properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminArquivoExportadorControllerSecurityTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private AdminArquivoExportadorService service;

  @Test
  void concessaoERevogacaoExigemAdminConfigurarECsrf() throws Exception {
    UUID alvo = UUID.randomUUID();
    when(service.alterar(any(), any(), any(), any())).thenReturn(detalhe(alvo));

    for (boolean conceder : List.of(true, false)) {
      String body = "{\"conceder\":" + conceder + ",\"versao\":0}";
      mockMvc.perform(patch("/api/admin/staff/{id}/arquivo-exportador", alvo)
              .with(authentication(token(PapelUsuario.ADMIN, true)))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"));
    }

    mockMvc.perform(patch("/api/admin/staff/{id}/arquivo-exportador", alvo)
            .with(authentication(token(PapelUsuario.ADMIN, true)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"conceder\":true,\"versao\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void papelTecnicoOuOutroAdminNaoPodeMudarConcessao() throws Exception {
    UUID alvo = UUID.randomUUID();
    for (var auth : List.of(
        token(PapelUsuario.ADMIN, false),
        token(PapelUsuario.MODERADOR, true),
        token(PapelUsuario.ARQUIVO_EXPORTADOR, true))) {
      mockMvc.perform(patch("/api/admin/staff/{id}/arquivo-exportador", alvo)
              .with(authentication(auth))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"conceder\":true,\"versao\":0}"))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(service);
  }

  private UsernamePasswordAuthenticationToken token(PapelUsuario papel, boolean configurar) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    if (configurar) {
      authorities.add(new SimpleGrantedAuthority("ADMIN_CONFIGURAR"));
    }
    var principal = new AdminUserPrincipal(
        UUID.randomUUID(), "Operador QA", "operador.qa@example.invalid", "hash",
        List.of(papel),
        configurar ? List.of(new AdminPermissionDto("ADMIN_CONFIGURAR", "Configurar administracao")) : List.of(),
        authorities, true);
    return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
  }

  private Detalhe detalhe(UUID id) {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-30T12:00:00Z");
    return new Detalhe(new Resumo(
        id, "Staff QA", "staff.qa@example.invalid", "ADMIN", "Administrador",
        "ATIVO", "Ativo", true, false, now, now, 0), List.of(), List.of());
  }
}
