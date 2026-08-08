package br.com.topsdojob.v3.web.publico.pagamento;

import br.com.topsdojob.v3.application.publico.pagamento.EfiPagamentoService;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPagamentoHistoricoDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutDto;
import br.com.topsdojob.v3.application.publico.pagamento.dto.EfiPixCheckoutRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<List<EfiPagamentoHistoricoDto>> historico(Authentication authentication) {
        return noStore(service.historico(authentication));
    }

    @PostMapping("/pix")
    public ResponseEntity<EfiPixCheckoutDto> criar(
            @RequestBody EfiPixCheckoutRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {
        return noStore(service.criar(body, idempotencyKey, authentication));
    }

    @GetMapping("/{pagamentoId}")
    public ResponseEntity<EfiPixCheckoutDto> consultar(
            @PathVariable UUID pagamentoId,
            Authentication authentication) {
        return noStore(service.consultar(pagamentoId, authentication));
    }

    @PostMapping("/{pagamentoId}/conciliar")
    public ResponseEntity<EfiPixCheckoutDto> conciliar(
            @PathVariable UUID pagamentoId,
            Authentication authentication,
            HttpServletRequest request) {
        return noStore(service.conciliar(
                pagamentoId,
                authentication,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
