package br.com.topsdojob.v3.web.admin.compliance;

import br.com.topsdojob.v3.application.admin.compliance.AdminComplianceVisitorService;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDecisaoResponseDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceDocumentoDto;
import br.com.topsdojob.v3.application.admin.compliance.dto.AdminComplianceRiscoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/compliance")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('SEGURANCA_GERENCIAR')")
public class AdminComplianceVisitorController {

  private final AdminComplianceVisitorService service;

  public AdminComplianceVisitorController(AdminComplianceVisitorService service) {
    this.service = service;
  }

  @GetMapping("/documentos")
  public ResponseEntity<List<AdminComplianceDocumentoDto>> listarDocumentos(
      @RequestParam(defaultValue = "50") int limite) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.listarDocumentos(limite));
  }

  @GetMapping("/documentos/{id}/arquivo")
  public ResponseEntity<byte[]> arquivo(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) {
    var result = service.carregarDocumento(id, actor, RequestIdContext.current(request));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(result.mimeType()))
        .contentLength(result.bytes().length)
        .body(result.bytes());
  }

  @PostMapping("/documentos/{id}/decidir")
  public AdminComplianceDocumentoDecisaoResponseDto decidir(
      @PathVariable UUID id,
      @RequestBody AdminComplianceDocumentoDecisaoRequestDto body,
      @AuthenticationPrincipal AdminUserPrincipal actor,
      HttpServletRequest request) {
    return service.decidir(
        id,
        body,
        actor,
        RequestIdContext.current(request));
  }

  @GetMapping("/risco")
  public ResponseEntity<List<AdminComplianceRiscoDto>> listarRisco(
      @RequestParam(defaultValue = "50") int limite) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.listarRisco(limite));
  }
}
