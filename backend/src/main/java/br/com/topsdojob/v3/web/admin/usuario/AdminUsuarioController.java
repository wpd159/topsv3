package br.com.topsdojob.v3.web.admin.usuario;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioConsultaService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioResumoDto;
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
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
public class AdminUsuarioController {

    private final AdminUsuarioConsultaService service;
    private final AdminUsuarioAtualizacaoService atualizacaoService;

    public AdminUsuarioController(
            AdminUsuarioConsultaService service,
            AdminUsuarioAtualizacaoService atualizacaoService) {
        this.service = service;
        this.atualizacaoService = atualizacaoService;
    }

    @GetMapping
    public ResponseEntity<AdminPaginaDto<AdminUsuarioResumoDto>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "TODOS") String kyc,
            @RequestParam(defaultValue = "RECENTES") String ordenacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return semCache(service.listar(termo, status, kyc, ordenacao, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUsuarioDetalheDto> detalhar(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminUserPrincipal ator) {
        return semCache(service.detalhar(id, ator));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminUsuarioDetalheDto> atualizarTelefone(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminUsuarioAtualizacaoRequestDto body,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(atualizacaoService.atualizarTelefone(
                id,
                body,
                ator,
                RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
