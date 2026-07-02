package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminOutboxConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminOutboxDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminOutboxListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusOutbox;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/outbox")
public class AdminOutboxController {

    private final AdminOutboxConsultaService service;

    public AdminOutboxController(AdminOutboxConsultaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MODERADOR') and hasAuthority('ANUNCIO_MODERAR'))")
    public AdminPaginaDto<AdminOutboxListaItemDto> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StatusOutbox status,
            @RequestParam(required = false) String tipoEvento,
            @RequestParam(required = false) String entidadeTipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime criadoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime criadoAte,
            @AuthenticationPrincipal AdminUserPrincipal actor) {
        return service.listar(page, size, status, tipoEvento, entidadeTipo, criadoDe, criadoAte, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MODERADOR') and hasAuthority('ANUNCIO_MODERAR'))")
    public AdminOutboxDetalheDto detalhar(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminUserPrincipal actor) {
        return service.detalhar(id, actor);
    }
}
