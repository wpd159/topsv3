package br.com.topsdojob.v3.web.admin.blog;

import br.com.topsdojob.v3.application.blog.BlogImagemService;
import br.com.topsdojob.v3.application.blog.BlogPostService;
import br.com.topsdojob.v3.application.blog.dto.BlogImagemDto;
import br.com.topsdojob.v3.application.blog.dto.BlogPostDto;
import br.com.topsdojob.v3.application.blog.dto.BlogPostRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/blog-posts")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminBlogPostController {

  private final BlogPostService service;
  private final BlogImagemService imagemService;

  public AdminBlogPostController(BlogPostService service, BlogImagemService imagemService) {
    this.service = service;
    this.imagemService = imagemService;
  }

  @GetMapping
  public List<BlogPostDto> listar(
      @RequestParam(required = false) String termo,
      @RequestParam(required = false) String status) {
    return service.listarAdmin(termo, status);
  }

  @GetMapping("/{id}")
  public BlogPostDto buscar(@PathVariable UUID id) {
    return service.buscarAdmin(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BlogPostDto criar(
      @RequestBody BlogPostRequest request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.criar(request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PutMapping("/{id}")
  public BlogPostDto atualizar(
      @PathVariable UUID id,
      @RequestBody BlogPostRequest request,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.atualizar(id, request, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/publicar")
  public BlogPostDto publicar(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.publicar(id, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/retirar")
  public BlogPostDto retirar(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.retirar(id, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping("/{id}/arquivar")
  public BlogPostDto arquivar(
      @PathVariable UUID id,
      @AuthenticationPrincipal AdminUserPrincipal ator,
      HttpServletRequest httpRequest) {
    return service.arquivar(id, atorId(ator), RequestIdContext.current(httpRequest));
  }

  @PostMapping(path = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public BlogImagemDto enviarImagem(
      @RequestPart("imagem") MultipartFile imagem,
      @RequestParam String tipo,
      @AuthenticationPrincipal AdminUserPrincipal ator) {
    return imagemService.enviar(imagem, tipo, atorId(ator));
  }

  private UUID atorId(AdminUserPrincipal ator) {
    return ator == null ? null : ator.usuarioId();
  }
}
