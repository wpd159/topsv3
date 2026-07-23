package br.com.topsdojob.v3.web.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioJuridicoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioOperacaoJuridicaDto;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminBloqueioJuridicoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminDesbloqueioJuridicoRequest;
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
public class AdminAnuncioJuridicoController {

  private final AdminAnuncioJuridicoService service;

  public AdminAnuncioJuridicoController(AdminAnuncioJuridicoService service) {
    this.service = service;
  }

  @PostMapping("/{id}/reativar")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioOperacaoJuridicaDto> reativar(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(service.reativar(id, administrador, RequestIdContext.current(request)));
  }

  @PostMapping("/{id}/bloqueio-juridico")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioOperacaoJuridicaDto> bloquearAnuncio(
      @PathVariable UUID id,
      @RequestBody AdminBloqueioJuridicoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(service.bloquearAnuncio(
        id, body, administrador, RequestIdContext.current(request)));
  }

  @PostMapping("/{id}/bloqueio-juridico/usuario")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioOperacaoJuridicaDto> bloquearAnuncioEUsuario(
      @PathVariable UUID id,
      @RequestBody AdminBloqueioJuridicoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(service.bloquearAnuncioEUsuario(
        id, body, administrador, RequestIdContext.current(request)));
  }

  @PostMapping("/{id}/desbloqueio-juridico")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioOperacaoJuridicaDto> desbloquearAnuncio(
      @PathVariable UUID id,
      @RequestBody(required = false) AdminDesbloqueioJuridicoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(service.desbloquearAnuncio(
        id, body, administrador, RequestIdContext.current(request)));
  }

  @PostMapping("/{id}/desbloqueio-juridico/usuario")
  @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<AdminAnuncioOperacaoJuridicaDto> desbloquearUsuario(
      @PathVariable UUID id,
      @RequestBody(required = false) AdminDesbloqueioJuridicoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(service.desbloquearUsuario(
        id, body, administrador, RequestIdContext.current(request)));
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
