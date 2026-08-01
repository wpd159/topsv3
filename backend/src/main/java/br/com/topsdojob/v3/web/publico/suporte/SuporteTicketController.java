package br.com.topsdojob.v3.web.publico.suporte;

import br.com.topsdojob.v3.application.suporte.SuporteDtos.CriarTicketRequest;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Mensagem;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.NaoLidas;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Pagina;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.ResponderTicketRequest;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.TicketDetalhe;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.TicketResumo;
import br.com.topsdojob.v3.application.suporte.SuporteTicketService;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/suporte/tickets")
public class SuporteTicketController {

    private final SuporteTicketService service;

    public SuporteTicketController(SuporteTicketService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Pagina<TicketResumo>> listar(
            @RequestParam(defaultValue = "TODOS") String grupo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return semCache(service.listar(grupo, page, size, authentication));
    }

    @PostMapping
    public ResponseEntity<TicketDetalhe> criar(
            @RequestBody(required = false) CriarTicketRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        return semCache(service.criar(
                body,
                idempotencyKey,
                authentication,
                RequestIdContext.current(request)));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketDetalhe> detalhar(
            @PathVariable UUID ticketId,
            Authentication authentication) {
        return semCache(service.detalhar(ticketId, authentication));
    }

    @GetMapping("/nao-lidas")
    public ResponseEntity<NaoLidas> naoLidas(Authentication authentication) {
        return semCache(service.naoLidas(authentication));
    }

    @PostMapping("/{ticketId}/mensagens")
    public ResponseEntity<Mensagem> responder(
            @PathVariable UUID ticketId,
            @RequestBody(required = false) ResponderTicketRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication,
            HttpServletRequest request) {
        return semCache(service.responder(
                ticketId,
                body == null ? null : body.mensagem(),
                idempotencyKey,
                authentication,
                RequestIdContext.current(request)));
    }

    @PostMapping("/{ticketId}/encerrar")
    public ResponseEntity<TicketDetalhe> encerrar(
            @PathVariable UUID ticketId,
            Authentication authentication,
            HttpServletRequest request) {
        return semCache(service.encerrar(
                ticketId,
                authentication,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
