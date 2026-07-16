package br.com.topsdojob.v3.web.admin.conteudo;

import br.com.topsdojob.v3.application.admin.conteudo.AdminCategoriaHomeService;
import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaAnuncioAdminDto;
import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaHomeAdminDto;
import br.com.topsdojob.v3.application.admin.conteudo.dto.CategoriaHomeAdminRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/categorias-home")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIGURAR')")
public class AdminCategoriaHomeController {

  private final AdminCategoriaHomeService service;

  public AdminCategoriaHomeController(AdminCategoriaHomeService service) {
    this.service = service;
  }

  @GetMapping
  public List<CategoriaHomeAdminDto> listar() {
    return service.listar();
  }

  @GetMapping("/categorias-canonicas")
  public List<CategoriaAnuncioAdminDto> listarCategoriasCanonicas() {
    return service.listarCategoriasCanonicas();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CategoriaHomeAdminDto criar(
      @RequestParam String categoriaCodigo,
      @RequestParam String nome,
      @RequestParam String descricao,
      @RequestParam Integer ordem,
      @RequestParam Boolean ativo,
      @RequestPart("imagem") MultipartFile imagem) {
    return service.criar(
        new CategoriaHomeAdminRequest(categoriaCodigo, nome, descricao, ordem, ativo),
        imagem);
  }

  @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CategoriaHomeAdminDto atualizar(
      @PathVariable UUID id,
      @RequestParam String categoriaCodigo,
      @RequestParam String nome,
      @RequestParam String descricao,
      @RequestParam Integer ordem,
      @RequestParam Boolean ativo,
      @RequestPart(value = "imagem", required = false) MultipartFile imagem) {
    return service.atualizar(
        id,
        new CategoriaHomeAdminRequest(categoriaCodigo, nome, descricao, ordem, ativo),
        imagem);
  }
}
