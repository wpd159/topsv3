package br.com.topsdojob.v3.web.admin.staff;

import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.AtualizarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.CriarRequest;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Detalhe;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Indicadores;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Pagina;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffDtos.Resumo;
import br.com.topsdojob.v3.application.admin.staff.AdminStaffService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminStaffController {
  private final AdminStaffService service;

  public AdminStaffController(AdminStaffService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<Pagina<Resumo>> listar(
      @RequestParam(required = false) String termo,
      @RequestParam(defaultValue = "TODOS") String papel,
      @RequestParam(defaultValue = "TODOS") String status,
      @RequestParam(defaultValue = "RECENTES") String ordenacao,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return semCache(service.listar(termo, papel, status, ordenacao, page, size));
  }

  @GetMapping("/indicadores")
  public ResponseEntity<Indicadores> indicadores() {
    return semCache(service.indicadores());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Detalhe> detalhar(@PathVariable UUID id) {
    return semCache(service.detalhar(id));
  }

  @PostMapping
  public ResponseEntity<Detalhe> criar(
      @RequestBody(required = false) CriarRequest body,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return semCache(service.criar(body, ator, RequestIdContext.current(request)));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<Detalhe> atualizar(
      @PathVariable UUID id,
      @RequestBody(required = false) AtualizarRequest body,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest request) {
    return semCache(service.atualizar(id, body, ator, RequestIdContext.current(request)));
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
