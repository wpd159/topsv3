package br.com.topsdojob.v3.web.admin.stories;

import br.com.topsdojob.v3.application.admin.stories.AdminStoryConfiguracaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryConfiguracaoRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stories/configuracao")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('PREMIUM_GERENCIAR')")
public class AdminStoryConfiguracaoController {

  private final AdminStoryConfiguracaoService service;

  public AdminStoryConfiguracaoController(AdminStoryConfiguracaoService service) {
    this.service = service;
  }

  @GetMapping
  public AdminStoryConfiguracaoDto consultar() {
    return service.consultar();
  }

  @PutMapping
  public AdminStoryConfiguracaoDto salvar(
      @RequestBody AdminStoryConfiguracaoRequest body,
      @AuthenticationPrincipal AdminUserPrincipal administrador,
      HttpServletRequest request) {
    return service.salvar(body, administrador, RequestIdContext.current(request));
  }
}
