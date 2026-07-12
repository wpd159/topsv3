package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.CategoriaHomePublicaDto;
import br.com.topsdojob.v3.application.publico.service.CategoriaHomePublicaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/categorias-home")
public class CategoriaHomePublicaController {

  private final CategoriaHomePublicaService service;

  public CategoriaHomePublicaController(CategoriaHomePublicaService service) {
    this.service = service;
  }

  @GetMapping
  public List<CategoriaHomePublicaDto> listar() {
    return service.listarAtivas();
  }
}
