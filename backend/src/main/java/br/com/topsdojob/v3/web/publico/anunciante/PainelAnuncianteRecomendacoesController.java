package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.PainelAnuncianteRecomendacoesService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnuncianteRecomendacoesDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/painel-anunciante/recomendacoes")
public class PainelAnuncianteRecomendacoesController {

    private final PainelAnuncianteRecomendacoesService service;

    public PainelAnuncianteRecomendacoesController(PainelAnuncianteRecomendacoesService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PainelAnuncianteRecomendacoesDto> consultar(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.consultar(authentication));
    }
}
