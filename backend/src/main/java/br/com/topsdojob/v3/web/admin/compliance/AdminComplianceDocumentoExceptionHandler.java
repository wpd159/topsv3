package br.com.topsdojob.v3.web.admin.compliance;

import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService;
import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceDocumentoAuditService.Etapa;
import br.com.topsdojob.v3.platform.error.ApiErrorResponse;
import br.com.topsdojob.v3.platform.error.GlobalExceptionHandler;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice(assignableTypes = AdminComplianceVisitorController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminComplianceDocumentoExceptionHandler {
  private static final String ARQUIVO = "/api/admin/compliance/documentos/{id}/arquivo";
  private final ObjectProvider<AdminComplianceDocumentoAuditService> auditProvider;
  private final GlobalExceptionHandler errors;

  public AdminComplianceDocumentoExceptionHandler(
      ObjectProvider<AdminComplianceDocumentoAuditService> auditProvider,
      GlobalExceptionHandler errors) {
    this.auditProvider = auditProvider;
    this.errors = errors;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> acessoNegado(
      AccessDeniedException exception, HttpServletRequest request) {
    if ("GET".equals(request.getMethod())
        && ARQUIVO.equals(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
        && request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variaveis
        && variaveis.get("id") instanceof String id) {
      // O argumento UUID ja foi validado pelo MVC antes da negativa de metodo.
      UUID documentoId = UUID.fromString(id);
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      UUID atorId = authentication != null && authentication.getPrincipal() instanceof AdminUserPrincipal ator
          ? ator.usuarioId() : null;
      String requestId = RequestIdContext.current(request);
      var audit = auditProvider.getObject();
      audit.registrar(documentoId, atorId, requestId, Etapa.TENTATIVA);
      audit.registrar(documentoId, atorId, requestId, Etapa.NEGADO);
    }
    return errors.handleAccessDenied(exception, request);
  }
}
