package br.com.topsdojob.v3.web.admin.blog;

import br.com.topsdojob.v3.application.blog.BlogCategoriaService;
import br.com.topsdojob.v3.application.blog.dto.BlogCategoriaDto;
import br.com.topsdojob.v3.application.blog.dto.BlogCategoriaRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/blog-categorias")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminBlogCategoriaController {

  private final BlogCategoriaService service;

  public AdminBlogCategoriaController(BlogCategoriaService service) {
    this.service = service;
  }

  @GetMapping
  public List<BlogCategoriaDto> listar() {
    return service.listarAdmin();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BlogCategoriaDto criar(
      @RequestBody BlogCategoriaRequest request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.criar(request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PutMapping("/{id}")
  public BlogCategoriaDto atualizar(
      @PathVariable UUID id,
      @RequestBody BlogCategoriaRequest request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.atualizar(id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @DeleteMapping("/{id}")
  public BlogCategoriaDto desativar(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.desativar(id, atorId(ator), RequestIdContext.current(httpRequest));
  }

  private UUID atorId(AdminUserPrincipal ator) {
    return ator == null ? null : ator.usuarioId();
  }
}
