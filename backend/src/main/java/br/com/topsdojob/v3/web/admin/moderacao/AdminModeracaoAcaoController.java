package br.com.topsdojob.v3.web.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminRemeterRevisaoRequestDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminModeracaoAcaoController {

    private final AdminModeracaoAcaoService service;

    public AdminModeracaoAcaoController(AdminModeracaoAcaoService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/moderacao/revisoes/{id}/decidir")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminAcaoModeracaoResponseDto decidirRevisao(
            @PathVariable UUID id,
            @RequestBody AdminDecidirRevisaoRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.decidirRevisao(id, request, actor, RequestIdContext.current(httpRequest));
    }

    @PostMapping("/api/admin/midias/{id}/decidir")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminAcaoModeracaoResponseDto decidirMidia(
            @PathVariable UUID id,
            @RequestBody AdminDecidirMidiaRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.decidirMidia(id, request, actor, RequestIdContext.current(httpRequest));
    }

    @PostMapping("/api/admin/anuncios/{id}/remeter-revisao")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminAcaoModeracaoResponseDto remeterAnuncioParaRevisao(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRemeterRevisaoRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.remeterAnuncioParaRevisao(id, request, actor, RequestIdContext.current(httpRequest));
    }
}
