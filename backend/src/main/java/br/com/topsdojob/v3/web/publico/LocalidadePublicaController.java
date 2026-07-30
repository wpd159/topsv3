package br.com.topsdojob.v3.web.publico;

import br.com.topsdojob.v3.application.publico.dto.AgregadoCidadePublicaDto;
import br.com.topsdojob.v3.application.publico.dto.DescobertaLocalidadesPublicaDto;
import br.com.topsdojob.v3.application.publico.service.LocalidadePublicaConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/localidades")
public class LocalidadePublicaController {

    private final LocalidadePublicaConsultaService consultaService;

    public LocalidadePublicaController(LocalidadePublicaConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    public DescobertaLocalidadesPublicaDto descobrir() {
        return consultaService.descobrir();
    }

    @GetMapping("/catalogo")
    public DescobertaLocalidadesPublicaDto catalogoCompleto() {
        return consultaService.catalogoCompleto();
    }

    @GetMapping("/{uf}/{cidade}")
    public AgregadoCidadePublicaDto agregadoCidade(
            @PathVariable String uf,
            @PathVariable String cidade) {
        return consultaService.agregadoCidade(uf, cidade);
    }
}
