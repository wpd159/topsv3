package br.com.topsdojob.v3.web.admin.documento;

import br.com.topsdojob.v3.application.admin.documento.AdminKycService;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoRequestDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycDecisaoResponseDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycUrlTemporariaDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documentos")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('DOCUMENTO_REVISAR')")
public class AdminKycController {

  private final AdminKycService service;

  public AdminKycController(AdminKycService service) {
    this.service = service;
  }

  @GetMapping
  public List<AdminKycEnvioDto> listarPendentes() {
    return service.listarPendentes();
  }

  @GetMapping("/envios/{envioId}")
  public AdminKycEnvioDto detalhar(@PathVariable UUID envioId) {
    return service.detalhar(envioId);
  }

  @GetMapping("/{documentoId}/url-temporaria")
  public AdminKycUrlTemporariaDto urlTemporaria(
      @PathVariable UUID documentoId,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return service.urlTemporaria(documentoId, ator, RequestIdContext.current(request));
  }

  @PostMapping("/envios/{envioId}/decidir")
  public AdminKycDecisaoResponseDto decidir(
      @PathVariable UUID envioId,
      @RequestBody AdminKycDecisaoRequestDto body,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return service.decidir(envioId, body, ator, RequestIdContext.current(request));
  }
}
