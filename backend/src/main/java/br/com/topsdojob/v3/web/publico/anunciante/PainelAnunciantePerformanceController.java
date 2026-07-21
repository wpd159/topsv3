package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.anunciante.PainelAnunciantePerformanceService;
import br.com.topsdojob.v3.application.publico.anunciante.dto.PainelAnunciantePerformanceDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/painel-anunciante/performance")
public class PainelAnunciantePerformanceController {

    private final PainelAnunciantePerformanceService service;

    public PainelAnunciantePerformanceController(PainelAnunciantePerformanceService service) {
        this.service = service;
    }

    @GetMapping
    public PainelAnunciantePerformanceDto consultar(Authentication authentication) {
        return service.consultar(authentication);
    }
}
