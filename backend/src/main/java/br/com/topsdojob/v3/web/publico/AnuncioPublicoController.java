package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.ListaStoriesPublicosDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.application.publico.service.AnuncioPublicoConsultaService;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaService;
import br.com.topsdojob.v3.application.publico.service.StoryPublicoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/anuncios")
public class AnuncioPublicoController {

    private final AnuncioPublicoConsultaService consultaService;
    private final MetricaPublicaService metricaService;
    private final StoryPublicoService storyService;

    public AnuncioPublicoController(
            AnuncioPublicoConsultaService consultaService,
            MetricaPublicaService metricaService,
            StoryPublicoService storyService) {
        this.consultaService = consultaService;
        this.metricaService = metricaService;
        this.storyService = storyService;
    }

    @GetMapping("/{slug}")
    public AnuncioDetalhePublicoDto buscarPorSlug(@PathVariable String slug, HttpServletRequest request) {
        return consultaService.buscarPorSlug(slug, request);
    }

    @GetMapping("/{slug}/stories")
    public ListaStoriesPublicosDto listarStories(@PathVariable String slug, HttpServletRequest request) {
        return storyService.listar(slug, request);
    }

    @PostMapping("/{slug}/visualizacao")
    public RegistrarVisualizacaoPublicaResponseDto registrarVisualizacao(
            @PathVariable String slug,
            @RequestBody(required = false) RegistrarVisualizacaoPublicaRequestDto request,
            HttpServletRequest httpRequest) {
        return metricaService.registrarVisualizacao(slug, request, httpRequest);
    }

    @PostMapping("/{slug}/clique-whatsapp")
    public CliqueWhatsappPublicoResponseDto registrarCliqueWhatsapp(
            @PathVariable String slug,
            @RequestBody(required = false) CliqueWhatsappPublicoRequestDto request,
            HttpServletRequest httpRequest) {
        return metricaService.registrarCliqueWhatsapp(slug, request, httpRequest);
    }
}
