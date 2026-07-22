package br.com.topsdojob.v3.web.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.publico.anunciante.dto.MeuAnuncioAtualizacaoRequestDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioAtualizacaoController {

    private final AdminAnuncioAtualizacaoService service;

    public AdminAnuncioAtualizacaoController(AdminAnuncioAtualizacaoService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminAnuncioDetalheDto> atualizar(
            @PathVariable UUID id,
            @RequestBody MeuAnuncioAtualizacaoRequestDto body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.atualizar(
                        id,
                        body,
                        administrador,
                        RequestIdContext.current(request)));
    }
}
