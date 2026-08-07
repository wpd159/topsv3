package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.AnuncioDetalhePublicoDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoRequestDto;
import br.com.topsdojob.v3.application.publico.dto.CliqueWhatsappPublicoResponseDto;
import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosCategoriaPublicaDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaRequestDto;
import br.com.topsdojob.v3.application.publico.dto.RegistrarVisualizacaoPublicaResponseDto;
import br.com.topsdojob.v3.application.publico.service.AnuncioPublicoConsultaService;
import br.com.topsdojob.v3.application.publico.service.ListagemPublicaConsultaService;
import br.com.topsdojob.v3.application.publico.service.MetricaPublicaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/anuncios")
public class AnuncioPublicoController {

    private final AnuncioPublicoConsultaService consultaService;
    private final ListagemPublicaConsultaService listagemService;
    private final MetricaPublicaService metricaService;

    public AnuncioPublicoController(
            AnuncioPublicoConsultaService consultaService,
            ListagemPublicaConsultaService listagemService,
            MetricaPublicaService metricaService) {
        this.consultaService = consultaService;
        this.listagemService = listagemService;
        this.metricaService = metricaService;
    }

    @GetMapping
    public ListaAnunciosCategoriaPublicaDto listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String anunciante,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho,
            @RequestParam(required = false) String ordemSeed) {
        return listagemService.listar(categoria, busca, anunciante, pagina, tamanho, ordemSeed);
    }

    @GetMapping("/{slug}")
    public AnuncioDetalhePublicoDto buscarPorSlug(@PathVariable String slug, HttpServletRequest request) {
        return consultaService.buscarPorSlug(slug, request);
    }

    @PostMapping("/{slug}/visualizacao")
    public RegistrarVisualizacaoPublicaResponseDto registrarVisualizacao(
            @PathVariable String slug,
            @RequestBody(required = false) RegistrarVisualizacaoPublicaRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return metricaService.registrarVisualizacao(slug, request, idempotencyKey, httpRequest);
    }

    @PostMapping("/{slug}/clique-whatsapp")
    public CliqueWhatsappPublicoResponseDto registrarCliqueWhatsapp(
            @PathVariable String slug,
            @RequestBody(required = false) CliqueWhatsappPublicoRequestDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        return metricaService.registrarCliqueWhatsapp(slug, request, idempotencyKey, httpRequest);
    }
}
