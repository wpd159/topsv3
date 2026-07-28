package br.com.topsdojob.v3.web.admin.denuncia;

import br.com.topsdojob.v3.application.denuncia.AdminDenunciaService;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.AlterarStatusRequest;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Detalhe;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Indicadores;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Pagina;
import br.com.topsdojob.v3.application.denuncia.DenunciaDtos.Resumo;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/admin/denuncias")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
public class AdminDenunciaController {

    private final AdminDenunciaService service;

    public AdminDenunciaController(AdminDenunciaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Pagina<Resumo>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "TODOS") String motivo,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return semCache(service.listar(termo, motivo, status, inicio, fim, page, size));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<Indicadores> indicadores() {
        return semCache(service.indicadores());
    }

    @GetMapping("/{denunciaId}")
    public ResponseEntity<Detalhe> detalhar(@PathVariable UUID denunciaId) {
        return semCache(service.detalhar(denunciaId));
    }

    @PatchMapping("/{denunciaId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<Detalhe> alterarStatus(
            @PathVariable UUID denunciaId,
            @RequestBody(required = false) AlterarStatusRequest body,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(service.alterarStatus(
                denunciaId,
                body == null ? null : body.status(),
                body == null ? null : body.providencia(),
                ator,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
