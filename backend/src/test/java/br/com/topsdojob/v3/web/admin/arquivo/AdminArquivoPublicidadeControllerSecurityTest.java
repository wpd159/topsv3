package br.com.topsdojob.v3.web.admin.arquivo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeDtos.Arquivo;
import br.com.topsdojob.v3.application.admin.arquivo.AdminArquivoPublicidadeService;
import br.com.topsdojob.v3.application.admin.arquivo.FinalidadeAcessoArquivoPublicidade;
import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminArquivoPublicidadeController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminArquivoPublicidadeControllerSecurityTest {
  @Autowired private MockMvc mockMvc;
  @MockBean private AdminArquivoPublicidadeService service;

  @Test
  void somenteAdminComPermissaoConsultaArquivoPrivado() throws Exception {
    UUID id = UUID.randomUUID();
    UUID midiaId = UUID.randomUUID();
    var finalidade = FinalidadeAcessoArquivoPublicidade.AUDITORIA_INTERNA;
    when(service.listar(eq(0), eq(20), any(), any(), eq(finalidade)))
        .thenReturn(new AdminPaginaDto<>(List.of(), 0, 20, 0, 0, true));
    var detalhe = new Detalhe(
        id, UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
        "REMUNERADA", "SIM", "ABRANGIDA", OffsetDateTime.now(), null, null,
        null, List.of());
    when(service.detalhar(eq(id), any(), any(), eq(finalidade), eq(false)))
        .thenReturn(detalhe);
    when(service.detalhar(eq(id), any(), any(), eq(finalidade), eq(true)))
        .thenReturn(detalhe);
    when(service.midia(eq(id), eq(midiaId), any(), any(), eq(finalidade)))
        .thenReturn(new Arquivo(new byte[] {1}, "image/png"));

    mockMvc.perform(post("/api/admin/registros/publicidade")
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}", id)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, true))))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/midias/{midiaId}/arquivo", id, midiaId)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, true))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.MODERADOR, true, true))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .param("finalidade", finalidade.name())
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, false, false))))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}", id)
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/admin/registros/publicidade")
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}", id)
            .param("finalidade", "TEXTO_LIVRE_COM_DADOS_PESSOAIS")
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, false))))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .with(csrf())
            .with(authentication(token(PapelUsuario.ADMIN, true, true))))
        .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/admin/registros/publicidade/{id}/exportacao", id)
            .param("finalidade", finalidade.name())
            .with(authentication(token(PapelUsuario.ADMIN, true, true))))
        .andExpect(status().isForbidden());
    verify(service, never()).detalhar(eq(id), any(), any(), eq(null), eq(false));
  }

  private UsernamePasswordAuthenticationToken token(
      PapelUsuario papel, boolean leitura, boolean exportacao) {
    var role = new SimpleGrantedAuthority("ROLE_" + papel.name());
    var authorities = new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();
    authorities.add(role);
    if (leitura) {
      authorities.add(new SimpleGrantedAuthority("ARQUIVO_PUBLICIDADE_LER"));
    }
    if (exportacao) {
      authorities.add(new SimpleGrantedAuthority("ARQUIVO_PUBLICIDADE_EXPORTAR"));
    }
    var principal = new AdminUserPrincipal(
        UUID.randomUUID(), "Operador", "operador@example.invalid", "hash",
        List.of(papel), leitura
            ? List.of(new AdminPermissionDto("ARQUIVO_PUBLICIDADE_LER", "Arquivo"))
            : List.of(), authorities, true);
    return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), authorities);
  }
}
