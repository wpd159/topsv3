package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.MeusAnunciosConsultaService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioDto;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/minha-conta/anuncios")
public class MeusAnunciosController {

    private final MeusAnunciosConsultaService consultaService;

    public MeusAnunciosController(MeusAnunciosConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping
    public List<MeuAnuncioDto> listar(Authentication authentication) {
        return consultaService.listar(authentication);
    }

    @GetMapping("/{slug}")
    public MeuAnuncioDto detalhar(@PathVariable String slug, Authentication authentication) {
        return consultaService.detalhar(slug, authentication);
    }
}
