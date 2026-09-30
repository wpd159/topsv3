package br.com.topsdojob.v3.web.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.AdminAprovacaoReconciliacaoService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminAprovacaoReconciliacaoService.Resultado;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminAprovacaoReconciliacaoController {
  private final AdminAprovacaoReconciliacaoService service;

  public AdminAprovacaoReconciliacaoController(AdminAprovacaoReconciliacaoService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/anuncios/{anuncioId}/aprovacao-operacoes/{operacaoId}/status")
  @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
  public ResponseEntity<Resultado> consultar(
      @PathVariable UUID anuncioId,
      @PathVariable UUID operacaoId,
      @RequestParam Integer versaoAnuncioEsperada,
      @RequestParam(required = false) UUID revisaoIdEsperada,
      @AuthenticationPrincipal AdminUserPrincipal actor) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
        service.consultar(anuncioId, operacaoId, versaoAnuncioEsperada, revisaoIdEsperada, actor));
  }
}
