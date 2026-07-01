package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.service.SeoPublicoConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/seo")
public class SeoPublicoController {

    private final SeoPublicoConsultaService consultaService;

    public SeoPublicoController(SeoPublicoConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/rota")
    public SeoRotaPublicaDto porRota(@RequestParam String caminho) {
        return consultaService.buscarPorCaminho(caminho);
    }
}
