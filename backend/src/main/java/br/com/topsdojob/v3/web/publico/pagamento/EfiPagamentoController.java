package br.com.topsdojob.v3.web.publico.pagamento;

import br.com.topsdojob.v3.application.publico.pagamento.EfiPagamentoService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/minha-conta/pagamentos")
public class EfiPagamentoController {

    private final EfiPagamentoService service;

    public EfiPagamentoController(EfiPagamentoService service) {
        this.service = service;
    }

    @PostMapping("/pix")
    public EfiPixCheckoutDto criar(
            @RequestBody EfiPixCheckoutRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        return service.criar(body, idempotencyKey, authentication);
    }

    @GetMapping("/{pagamentoId}")
    public EfiPixCheckoutDto consultar(
            @PathVariable UUID pagamentoId,
            Authentication authentication) {
        return service.consultar(pagamentoId, authentication);
    }

    @PostMapping("/{pagamentoId}/conciliar")
    public EfiPixCheckoutDto conciliar(
            @PathVariable UUID pagamentoId,
            Authentication authentication,
            HttpServletRequest request) {
        return service.conciliar(
                pagamentoId,
                authentication,
                RequestIdContext.current(request));
    }
}
