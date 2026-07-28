package br.com.topsdojob.v3.web.admin.sugestao;

import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.AlterarStatusRequest;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Detalhe;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Indicadores;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Pagina;
import br.com.topsdojob.v3.application.sugestao.SugestaoDtos.Resumo;
import br.com.topsdojob.v3.application.sugestao.SugestaoService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sugestoes")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('SUPORTE_ATENDER')")
public class AdminSugestaoController {

    private final SugestaoService service;

    public AdminSugestaoController(SugestaoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Pagina<Resumo>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return semCache(service.listar(termo, status, page, size));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<Indicadores> indicadores() {
        return semCache(service.indicadores());
    }

    @GetMapping("/{sugestaoId}")
    public ResponseEntity<Detalhe> detalhar(@PathVariable UUID sugestaoId) {
        return semCache(service.detalhar(sugestaoId));
    }

    @PatchMapping("/{sugestaoId}/status")
    public ResponseEntity<Detalhe> alterarStatus(
            @PathVariable UUID sugestaoId,
            @RequestBody(required = false) AlterarStatusRequest body,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(service.alterarStatus(
                sugestaoId,
                body,
                ator,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
