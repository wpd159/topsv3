package br.com.topsdojob.v3.web.admin.suporte;

import br.com.topsdojob.v3.application.suporte.AdminSuporteService;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.AdminTicketDetalhe;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.AdminTicketResumo;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.AlterarStatusRequest;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Indicadores;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Mensagem;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.Pagina;
import br.com.topsdojob.v3.application.suporte.SuporteDtos.ResponderTicketRequest;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tickets")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('SUPORTE_ATENDER')")
public class AdminSuporteController {

    private final AdminSuporteService service;

    public AdminSuporteController(AdminSuporteService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Pagina<AdminTicketResumo>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "TODOS") String categoria,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "RECENTES") String ordenacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return semCache(service.listar(
                termo,
                categoria,
                status,
                ordenacao,
                page,
                size));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<Indicadores> indicadores() {
        return semCache(service.indicadores());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<AdminTicketDetalhe> detalhar(@PathVariable UUID ticketId) {
        return semCache(service.detalhar(ticketId));
    }

    @PostMapping("/{ticketId}/mensagens")
    public ResponseEntity<Mensagem> responder(
            @PathVariable UUID ticketId,
            @RequestBody(required = false) ResponderTicketRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(service.responder(
                ticketId,
                body == null ? null : body.mensagem(),
                idempotencyKey,
                ator,
                RequestIdContext.current(request)));
    }

    @PatchMapping("/{ticketId}/status")
    public ResponseEntity<AdminTicketDetalhe> alterarStatus(
            @PathVariable UUID ticketId,
            @RequestBody(required = false) AlterarStatusRequest body,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(service.alterarStatus(
                ticketId,
                body == null ? null : body.status(),
                ator,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
