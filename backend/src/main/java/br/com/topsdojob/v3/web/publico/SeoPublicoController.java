package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.SeoRotaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.SitemapAnuncioPublicoDto;
import br.com.topsdojob.v3.application.publico.service.SeoPublicoConsultaService;
import br.com.topsdojob.v3.application.publico.service.SitemapPublicoConsultaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/seo")
public class SeoPublicoController {

    private final SeoPublicoConsultaService consultaService;
    private final SitemapPublicoConsultaService sitemapService;

    public SeoPublicoController(
            SeoPublicoConsultaService consultaService,
            SitemapPublicoConsultaService sitemapService) {
        this.consultaService = consultaService;
        this.sitemapService = sitemapService;
    }

    @GetMapping("/rota")
    public SeoRotaPublicaDto porRota(@RequestParam String caminho) {
        return consultaService.buscarPorCaminho(caminho);
    }

    @GetMapping("/sitemap")
    public List<SitemapAnuncioPublicoDto> sitemap() {
        return sitemapService.listarAnunciosIndexaveis();
    }
}
