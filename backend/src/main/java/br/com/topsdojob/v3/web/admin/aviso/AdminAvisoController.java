package br.com.topsdojob.v3.web.admin.aviso;

import br.com.topsdojob.v3.application.aviso.AvisoDtos.AdminItem;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Edicao;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Indicadores;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Pagina;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.Versao;
import br.com.topsdojob.v3.application.aviso.AvisoService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/avisos")
public class AdminAvisoController {

  private static final String LEITURA =
      "hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ADMIN_CONFIGURAR')";
  private static final String MUTACAO =
      "hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')";

  private final AvisoService service;

  public AdminAvisoController(AvisoService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize(LEITURA)
  public Pagina<AdminItem> listar(
      @RequestParam(required = false) String termo,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String localExibicao,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.listarAdmin(termo, status, localExibicao, page, size);
  }

  @GetMapping("/indicadores")
  @PreAuthorize(LEITURA)
  public Indicadores indicadores() {
    return service.indicadores();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(MUTACAO)
  public AdminItem criar(
      @RequestBody Edicao request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.criar(
        request,
        ator == null ? null : ator.usuarioId(),
        ator == null ? null : ator.nome(),
        idempotencyKey,
        RequestIdContext.current(httpRequest));
  }

  @PutMapping("/{id}")
  @PreAuthorize(MUTACAO)
  public AdminItem atualizar(
      @PathVariable UUID id,
      @RequestBody Edicao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.atualizar(
        id,
        request,
        ator == null ? null : ator.usuarioId(),
        RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/publicar")
  @PreAuthorize(MUTACAO)
  public AdminItem publicar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.publicar(
        id,
        request,
        ator == null ? null : ator.usuarioId(),
        RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/retirar")
  @PreAuthorize(MUTACAO)
  public AdminItem retirar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.retirar(
        id,
        request,
        ator == null ? null : ator.usuarioId(),
        RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/arquivar")
  @PreAuthorize(MUTACAO)
  public AdminItem arquivar(
      @PathVariable UUID id,
      @RequestBody Versao request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.arquivar(
        id,
        request,
        ator == null ? null : ator.usuarioId(),
        RequestIdContext.current(httpRequest));
  }
}
