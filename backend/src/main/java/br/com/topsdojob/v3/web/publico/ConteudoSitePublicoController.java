package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.conteudo.ConteudoSiteService;
import br.com.topsdojob.v3.application.conteudo.dto.ConteudoSiteDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/conteudos-site")
public class ConteudoSitePublicoController {

  private final ConteudoSiteService service;

  public ConteudoSitePublicoController(ConteudoSiteService service) {
    this.service = service;
  }

  @GetMapping
  public List<ConteudoSiteDto> listar() {
    return service.listarPublicados();
  }
}
