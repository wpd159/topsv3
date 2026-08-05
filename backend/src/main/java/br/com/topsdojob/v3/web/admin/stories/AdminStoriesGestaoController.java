package br.com.topsdojob.v3.web.admin.stories;

import br.com.topsdojob.v3.application.admin.stories.AdminStoriesGestaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoriesPaginaDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryRemocaoRequest;
import br.com.topsdojob.v3.application.publico.anunciante.StoryEncerramentoService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.StoryEncerramentoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
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
@RequestMapping("/api/admin/stories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoriesGestaoController {

  private final AdminStoriesGestaoService gestaoService;
  private final StoryEncerramentoService encerramentoService;

  public AdminStoriesGestaoController(
      AdminStoriesGestaoService gestaoService,
      StoryEncerramentoService encerramentoService) {
    this.gestaoService = gestaoService;
    this.encerramentoService = encerramentoService;
  }

  @GetMapping("/gestao")
  public ResponseEntity<AdminStoriesPaginaDto> listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return semCache(gestaoService.listar(page, size));
  }

  @PostMapping("/{storyId}/remover")
  public ResponseEntity<StoryEncerramentoDto> remover(
      @PathVariable UUID storyId,
      @RequestBody AdminStoryRemocaoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return semCache(encerramentoService.removerComoAdmin(
        storyId,
        body,
        administrador,
        RequestIdContext.current(request)));
  }

  private <T> ResponseEntity<T> semCache(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
