package br.com.topsdojob.v3.web.admin.usuario;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoException;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioConsultaService;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioExclusaoBloqueadaException;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioExclusaoService;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoErroDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoElegibilidadeDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoErroDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoRequestDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioExclusaoResultadoDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioIndicadoresDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioResumoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
public class AdminUsuarioController {

    private final AdminUsuarioConsultaService service;
    private final AdminUsuarioAtualizacaoService atualizacaoService;
    private final AdminUsuarioExclusaoService exclusaoService;

    public AdminUsuarioController(
            AdminUsuarioConsultaService service,
            AdminUsuarioAtualizacaoService atualizacaoService,
            AdminUsuarioExclusaoService exclusaoService) {
        this.service = service;
        this.atualizacaoService = atualizacaoService;
        this.exclusaoService = exclusaoService;
    }

    @GetMapping
    public ResponseEntity<AdminPaginaDto<AdminUsuarioResumoDto>> listar(
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "TODOS") String kyc,
            @RequestParam(defaultValue = "TODOS") String grupo,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(defaultValue = "RECENTES") String ordenacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return semCache(service.listar(
                termo,
                status,
                kyc,
                grupo,
                uf,
                cidade,
                ordenacao,
                page,
                size));
    }

    @GetMapping("/indicadores")
    public ResponseEntity<AdminUsuarioIndicadoresDto> indicadores() {
        return semCache(service.indicadores());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUsuarioDetalheDto> detalhar(
            @PathVariable UUID id,
            @AuthenticationPrincipal AdminUserPrincipal ator) {
        return semCache(service.detalhar(id, ator));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminUsuarioDetalheDto> atualizar(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminUsuarioAtualizacaoRequestDto body,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(atualizacaoService.atualizar(
                id,
                body,
                ator,
                RequestIdContext.current(request)));
    }

    @GetMapping("/{id}/exclusao")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminUsuarioExclusaoElegibilidadeDto> elegibilidadeExclusao(
            @PathVariable UUID id) {
        return semCache(exclusaoService.elegibilidade(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminUsuarioExclusaoResultadoDto> excluir(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminUsuarioExclusaoRequestDto body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return semCache(exclusaoService.excluir(
                id,
                body,
                idempotencyKey,
                ator,
                RequestIdContext.current(request)));
    }

    @ExceptionHandler(AdminUsuarioAtualizacaoException.class)
    public ResponseEntity<AdminUsuarioAtualizacaoErroDto> handleAtualizacao(
            AdminUsuarioAtualizacaoException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore())
                .body(new AdminUsuarioAtualizacaoErroDto(
                        exception.status().value() == 409 ? "CONFLITO_CADASTRAL" : "DADOS_INVALIDOS",
                        exception.getMessage(),
                        exception.erros(),
                        RequestIdContext.current(request)));
    }

    @ExceptionHandler(AdminUsuarioExclusaoBloqueadaException.class)
    public ResponseEntity<AdminUsuarioExclusaoErroDto> handleExclusaoBloqueada(
            AdminUsuarioExclusaoBloqueadaException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(409)
                .cacheControl(CacheControl.noStore())
                .body(new AdminUsuarioExclusaoErroDto(
                        "USUARIO_NAO_EXCLUIVEL",
                        exception.getMessage(),
                        exception.bloqueios(),
                        RequestIdContext.current(request)));
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
