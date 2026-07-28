package br.com.topsdojob.v3.web.publico.faq;

import br.com.topsdojob.v3.application.faq.FaqDtos.Item;
import br.com.topsdojob.v3.application.faq.FaqService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/faqs")
public class FaqPublicaController {

  private final FaqService service;

  public FaqPublicaController(FaqService service) {
    this.service = service;
  }

  @GetMapping
  public List<Item> listar() {
    return service.listarPublicadas();
  }
}
