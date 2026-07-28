package br.com.topsdojob.v3.web.publico.sugestao;

import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Criacao;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.CriarRequest;
import br.com.topsdojob.v3.application.sugestao.SugestaoService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/sugestoes")
public class SugestaoPublicaController {

    private final SugestaoService service;

    public SugestaoPublicaController(SugestaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Criacao> criar(
            @RequestBody(required = false) CriarRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.criar(
                        body,
                        idempotencyKey,
                        authentication,
                        RequestIdContext.current(request)));
    }
}
