package br.com.topsdojob.v3.web.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioRemocaoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioRemocaoDto;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioRemocaoRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioRemocaoController {

  private final AdminAnuncioRemocaoService service;

  public AdminAnuncioRemocaoController(AdminAnuncioRemocaoService service) {
    this.service = service;
  }

  @PostMapping("/{id}/remocao-logica")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioRemocaoDto> remover(
      @PathVariable UUID id,
      @RequestBody AdminAnuncioRemocaoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(service.remover(id, body, administrador, RequestIdContext.current(request)));
  }
}
