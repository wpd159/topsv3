package br.com.topsdojob.v3.web.admin.compliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService;
import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService.Etapa;
import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceVisitorService;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.PapelUsuario;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import br.com.topsdojob.v3.security.config.AdminSecurityErrorWriter;
import br.com.topsdojob.v3.security.config.SecurityConfig;
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

@WebMvcTest(controllers = AdminComplianceVisitorController.class, properties = "app.env=homologacao")
@Import({SecurityConfig.class, AdminSecurityErrorWriter.class})
class AdminComplianceDocumentoHttpAuditTest {
  @Autowired MockMvc mvc;
  @MockBean AdminComplianceVisitorService service;
  @MockBean AdminComplianceDocumentoAuditService audit;

  @Test
  void negativaRbacHttpRegistraAtorDocumentoECorrelacaoSemLerArquivo() throws Exception {
    UUID documentoId = UUID.randomUUID();
    var order = inOrder(audit);
    for (var identity : List.of(token(PapelUsuario.ADMIN, false), token(PapelUsuario.MODERADOR, true))) {
      UUID atorId = ((AdminUserPrincipal) identity.getPrincipal()).usuarioId();
      String requestId = "negativa-" + atorId;
      mvc.perform(get("/api/admin/compliance/documentos/{id}/arquivo", documentoId)
              .header("X-Request-Id", requestId).with(authentication(identity)))
          .andExpect(status().isForbidden())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.code").value("FORBIDDEN"))
          .andExpect(jsonPath("$.message").value("Acesso negado."))
          .andExpect(jsonPath("$.requestId").value(requestId));
      order.verify(audit).registrar(documentoId, atorId, requestId, Etapa.TENTATIVA);
      order.verify(audit).registrar(documentoId, atorId, requestId, Etapa.NEGADO);
    }
    verifyNoMoreInteractions(audit);
    verifyNoInteractions(service);
  }

  @Test
  void outraRotaNegadaNaoCriaAuditoriaDeArquivo() throws Exception {
    mvc.perform(get("/api/admin/compliance/risco")
            .with(authentication(token(PapelUsuario.ADMIN, false))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/compliance/documentos")
            .with(authentication(token(PapelUsuario.MODERADOR, true))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(audit, service);
  }

  @Test
  void permissaoValidaPreservaBytesHeadersEPrincipalSemAuditoriaDeNegativa() throws Exception {
    UUID documentoId = UUID.randomUUID();
    var identity = token(PapelUsuario.ADMIN, true);
    var ator = (AdminUserPrincipal) identity.getPrincipal();
    when(service.carregarDocumento(eq(documentoId), eq(ator), any()))
        .thenReturn(new AdminComplianceVisitorService.DocumentoPrivado(new byte[] {1, 2, 3}, "application/pdf"));
    mvc.perform(get("/api/admin/compliance/documentos/{id}/arquivo", documentoId)
            .with(authentication(identity)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(new byte[] {1, 2, 3}))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    verifyNoInteractions(audit);
  }

  private UsernamePasswordAuthenticationToken token(PapelUsuario papel, boolean permissao) {
    var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + papel.name()));
    if (permissao) authorities.add(new SimpleGrantedAuthority("SEGURANCA_GERENCIAR"));
    var principal = new AdminUserPrincipal(UUID.randomUUID(), "Operador sintetico", "admin@example.invalid", "n/a",
        List.of(papel), List.of(), List.copyOf(authorities), true);
    return new UsernamePasswordAuthenticationToken(principal, "n/a", authorities);
  }
}
