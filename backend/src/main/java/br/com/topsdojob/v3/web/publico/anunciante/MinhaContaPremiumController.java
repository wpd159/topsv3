package br.com.topsdojob.v3.web.publico.anunciante;

import br.com.topsdojob.v3.application.publico.premium.MinhaContaPremiumService;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumRequest;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaCompraPremiumResultadoDto;
import br.com.topsdojob.v3.application.publico.premium.dto.MinhaMonetizacaoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/minha-conta/monetizacao")
public class MinhaContaPremiumController {

    private final MinhaContaPremiumService service;

    public MinhaContaPremiumController(MinhaContaPremiumService service) {
        this.service = service;
    }

    @GetMapping
    public MinhaMonetizacaoDto consultar(
            @RequestParam(required = false) String anuncioSlug,
            Authentication authentication) {
        return service.consultar(anuncioSlug, authentication);
    }

    @PostMapping("/compras")
    public MinhaCompraPremiumResultadoDto comprar(
            @RequestBody MinhaCompraPremiumRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        return service.comprar(
                body,
                idempotencyKey,
                authentication,
                RequestIdContext.current(request));
    }
}
