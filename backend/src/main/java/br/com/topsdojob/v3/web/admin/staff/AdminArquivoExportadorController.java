package br.com.topsdojob.v3.web.admin.staff;

import br.com.topsdojob.v3.application.admin.staff.AdminArquivoExportadorService;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.ExportadorRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminArquivoExportadorController {
  private final AdminArquivoExportadorService service;

  public AdminArquivoExportadorController(AdminArquivoExportadorService service) {
    this.service = service;
  }

  @PatchMapping("/{id}/arquivo-exportador")
  public ResponseEntity<Detalhe> alterar(
      @PathVariable UUID id,
      @RequestBody(required = false) ExportadorRequest body,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.alterar(id, body, ator, RequestIdContext.current(request)));
  }
}
