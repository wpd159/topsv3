package br.com.topsdojob.v3.web.admin.moderacao;

import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoAcaoService;
import br.com.topsdojob.v3.application.admin.moderacao.AdminModeracaoFotosLoteService;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminAcaoModeracaoResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirMidiaRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirFotosLoteResponseDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminDecidirRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminRemeterRevisaoRequestDto;
import br.com.topsdojob.v3.application.admin.moderacao.dto.AdminReclassificarMidiaRequestDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminModeracaoAcaoController {

    private final AdminModeracaoAcaoService service;
    private final AdminModeracaoFotosLoteService fotosLoteService;

    public AdminModeracaoAcaoController(
            AdminModeracaoAcaoService service,
            AdminModeracaoFotosLoteService fotosLoteService) {
        this.service = service;
        this.fotosLoteService = fotosLoteService;
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

    @PostMapping("/api/admin/anuncios/{id}/aprovar")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminAcaoModeracaoResponseDto aprovarEPublicarAnuncio(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.aprovarEPublicarAnuncio(id, actor, RequestIdContext.current(httpRequest));
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

    @PostMapping("/api/admin/anuncios/{id}/midias/decisoes")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public ResponseEntity<AdminDecidirFotosLoteResponseDto> decidirFotosEmLote(
            @PathVariable UUID id,
            @RequestBody AdminDecidirFotosLoteRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        var response = fotosLoteService.decidir(
                id,
                request,
                actor,
                RequestIdContext.current(httpRequest));
        return ResponseEntity.status(statusFotosLote(response)).body(response);
    }

    private HttpStatus statusFotosLote(AdminDecidirFotosLoteResponseDto response) {
        if (response == null || response.falhas() == 0) {
            return HttpStatus.OK;
        }
        int sucessos = response.aprovadas() + response.excluidas() + response.jaProcessadas();
        if (sucessos > 0) {
            return HttpStatus.MULTI_STATUS;
        }
        Set<String> codigos = response.resultados().stream()
                .map(item -> item.codigo())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (codigos.stream().anyMatch(Set.of(
                "FALHA_TECNICA",
                "TRANSACAO_CLEANUP_INDISPONIVEL")::contains)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (codigos.stream().anyMatch(Set.of(
                "FALHA_OPERACIONAL_R2",
                "OBJETO_PERMANECE_NO_R2",
                "STORAGE_INDISPONIVEL")::contains)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (codigos.contains("MIDIA_NAO_ENCONTRADA")) {
            return HttpStatus.NOT_FOUND;
        }
        if (codigos.contains("FALHA_DE_VALIDACAO")) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.CONFLICT;
    }

    @PostMapping("/api/admin/midias/{id}/reclassificar")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('MIDIA_REVISAR')")
    public AdminAcaoModeracaoResponseDto reclassificarMidia(
            @PathVariable UUID id,
            @RequestBody AdminReclassificarMidiaRequestDto request,
            @AuthenticationPrincipal AdminUserPrincipal actor,
            HttpServletRequest httpRequest) {
        return service.reclassificarMidia(id, request, actor, RequestIdContext.current(httpRequest));
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
