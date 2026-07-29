package br.com.topsdojob.v3.web.publico.aviso;

import br.com.topsdojob.v3.application.aviso.AvisoDtos.Pagina;
import br.com.topsdojob.v3.application.aviso.AvisoDtos.PublicoItem;
import br.com.topsdojob.v3.application.aviso.AvisoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/avisos")
public class AvisoPublicoController {

  private final AvisoService service;

  public AvisoPublicoController(AvisoService service) {
    this.service = service;
  }

  @GetMapping
  public Pagina<PublicoItem> listar(
      @RequestParam String localExibicao,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int size) {
    return service.listarPublicos(localExibicao, page, size);
  }
}
