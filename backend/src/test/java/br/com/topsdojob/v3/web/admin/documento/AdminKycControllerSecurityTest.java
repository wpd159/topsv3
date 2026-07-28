package br.com.topsdojob.v3.web.admin.documento;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.auth.dto.AdminPermissionDto;
import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = AdminKycController.class,
    properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminKycControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AdminKycService service;

  @Test
  void somenteAdminComPermissaoECsrfPodeAdicionarDocumento() throws Exception {
    UUID usuarioId = UUID.randomUUID();
    UUID envioId = UUID.randomUUID();
    when(service.enviarAdministrativamente(
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminKycEnvioDto(
            envioId,
            usuarioId,
            "QA Civil",
            "***.***.***-09",
            "1990-01-01",
            "PENDENTE",
            null,
            OffsetDateTime.parse("2026-07-27T10:00:00Z"),
            null,
            List.of()));
    var file = new MockMultipartFile(
        "documentoUnico",
        "qa.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});

    mockMvc.perform(multipart("/api/admin/documentos/usuarios/{usuarioId}/envios", usuarioId)
            .file(file)
            .param("tipoDocumento", "IDENTIDADE")
            .param("modoDocumento", "UNICO")
            .header("Idempotency-Key", "qa-doc-once")
            .with(authentication(token(PapelUsuario.ADMIN)))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envioId").value(envioId.toString()));

    mockMvc.perform(multipart("/api/admin/documentos/usuarios/{usuarioId}/envios", usuarioId)
            .file(file)
            .param("tipoDocumento", "IDENTIDADE")
            .param("modoDocumento", "UNICO")
            .header("Idempotency-Key", "qa-doc-moderator")
            .with(authentication(token(PapelUsuario.MODERADOR)))
            .with(csrf()))
        .andExpect(status().isForbidden());

    mockMvc.perform(multipart("/api/admin/documentos/usuarios/{usuarioId}/envios", usuarioId)
            .file(file)
            .param("tipoDocumento", "IDENTIDADE")
            .param("modoDocumento", "UNICO")
            .header("Idempotency-Key", "qa-doc-no-csrf")
            .with(authentication(token(PapelUsuario.ADMIN))))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken token(PapelUsuario papel) {
    var role = new SimpleGrantedAuthority("ROLE_" + papel.name());
    var permission = new SimpleGrantedAuthority("DOCUMENTO_REVISAR");
    var principal = new AdminUserPrincipal(
        UUID.randomUUID(),
        "Operador",
        "operador@example.invalid",
        "hash",
        List.of(papel),
        List.of(new AdminPermissionDto("DOCUMENTO_REVISAR", "Documento")),
        List.of(role, permission),
        true);
    return new UsernamePasswordAuthenticationToken(
        principal,
        principal.getPassword(),
        List.of(role, permission));
  }
}
