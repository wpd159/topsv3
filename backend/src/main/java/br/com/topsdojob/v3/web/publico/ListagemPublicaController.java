package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.ListaAnunciosPublicaDto;
import br.com.topsdojob.v3.application.publico.service.ListagemPublicaConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/acompanhantes")
public class ListagemPublicaController {

    private final ListagemPublicaConsultaService consultaService;

    public ListagemPublicaController(ListagemPublicaConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/{uf}/{cidade}")
    public ListaAnunciosPublicaDto porCidade(
            @PathVariable String uf,
            @PathVariable String cidade,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return consultaService.porCidade(uf, cidade, pagina, tamanho);
    }

    @GetMapping("/{uf}/{cidade}/{bairro}")
    public ListaAnunciosPublicaDto porBairro(
            @PathVariable String uf,
            @PathVariable String cidade,
            @PathVariable String bairro,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return consultaService.porBairro(uf, cidade, bairro, pagina, tamanho);
    }
}
