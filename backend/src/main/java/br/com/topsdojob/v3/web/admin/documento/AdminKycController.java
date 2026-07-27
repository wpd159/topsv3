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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<List<AdminKycEnvioDto>> listarPendentes() {
    return semCache(service.listarPendentes());
  }

  @GetMapping("/envios/{envioId}")
  public ResponseEntity<AdminKycEnvioDto> detalhar(@PathVariable UUID envioId) {
    return semCache(service.detalhar(envioId));
  }

  @GetMapping("/{documentoId}/url-temporaria")
  public ResponseEntity<AdminKycUrlTemporariaDto> urlTemporaria(
      @PathVariable UUID documentoId,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return semCache(service.urlTemporaria(documentoId, ator, RequestIdContext.current(request)));
  }

  @GetMapping(value = "/{documentoId}/miniatura", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> miniatura(
      @PathVariable UUID documentoId,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    var thumbnail = service.miniatura(documentoId, ator, RequestIdContext.current(request));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .eTag(thumbnail.etag())
        .contentType(MediaType.parseMediaType(thumbnail.contentType()))
        .body(thumbnail.content());
  }

  @PostMapping("/envios/{envioId}/decidir")
  public AdminKycDecisaoResponseDto decidir(
      @PathVariable UUID envioId,
      @RequestBody AdminKycDecisaoRequestDto body,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return service.decidir(envioId, body, ator, RequestIdContext.current(request));
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
