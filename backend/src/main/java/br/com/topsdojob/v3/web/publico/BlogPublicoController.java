package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.blog.BlogCategoriaService;
import br.com.topsdojob.v3.application.blog.BlogPostService;
import br.com.topsdojob.v3.application.blog.dto.BlogCategoriaDto;
import br.com.topsdojob.v3.application.blog.dto.BlogPostDto;
import br.com.topsdojob.v3.application.blog.dto.BlogSitemapDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class BlogPublicoController {

  private final BlogPostService postService;
  private final BlogCategoriaService categoriaService;

  public BlogPublicoController(
      BlogPostService postService,
      BlogCategoriaService categoriaService) {
    this.postService = postService;
    this.categoriaService = categoriaService;
  }

  @GetMapping("/blog-posts/public")
  public List<BlogPostDto> listarPosts() {
    return postService.listarPublicados();
  }

  @GetMapping("/blog-posts/public/sitemap")
  public List<BlogSitemapDto> sitemap() {
    return postService.sitemap();
  }

  @GetMapping("/blog-posts/public/categoria/{slug}")
  public List<BlogPostDto> listarPorCategoria(@PathVariable String slug) {
    return postService.listarPublicadosPorCategoria(slug);
  }

  @GetMapping("/blog-posts/public/{slug}")
  public BlogPostDto buscarPost(@PathVariable String slug) {
    return postService.buscarPublicado(slug);
  }

  @GetMapping("/blog-categorias/public")
  public List<BlogCategoriaDto> listarCategorias() {
    return categoriaService.listarPublicas();
  }
}
