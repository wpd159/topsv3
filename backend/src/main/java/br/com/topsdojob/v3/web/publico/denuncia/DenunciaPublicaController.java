package br.com.topsdojob.v3.web.publico.denuncia;

import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaRequest;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.CriarDenunciaResponse;
import br.com.topsdojob.v3.application.denuncia.DenunciaPublicaService;
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
@RequestMapping("/api/public/denuncias")
public class DenunciaPublicaController {

    private final DenunciaPublicaService service;

    public DenunciaPublicaController(DenunciaPublicaService service) {
        this.service = service;
    }

    @PostMapping("/abrir")
    public ResponseEntity<CriarDenunciaResponse> criar(
            @RequestBody(required = false) CriarDenunciaRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.criar(
                        body,
                        idempotencyKey,
                        authentication,
                        request,
                        RequestIdContext.current(request)));
    }
}
