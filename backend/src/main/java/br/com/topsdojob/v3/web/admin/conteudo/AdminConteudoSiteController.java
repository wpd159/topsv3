package br.com.topsdojob.v3.web.admin.conteudo;

import br.com.topsdojob.v3.application.conteudo.ConteudoSiteService;
import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteDto;
import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteRequest;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conteudos-site")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminConteudoSiteController {

  private final ConteudoSiteService service;

  public AdminConteudoSiteController(ConteudoSiteService service) {
    this.service = service;
  }

  @GetMapping
  public List<ConteudoSiteDto> listar() {
    return service.listarAdministracao();
  }

  @PutMapping("/{contentKey}")
  public ConteudoSiteDto publicar(
      @PathVariable String contentKey,
      @RequestBody ConteudoSiteRequest request,
      @AuthenticationPrincipal AdminUserPrincipal ator) {
    return service.publicar(contentKey, request, ator == null ? null : ator.usuarioId());
  }
}
