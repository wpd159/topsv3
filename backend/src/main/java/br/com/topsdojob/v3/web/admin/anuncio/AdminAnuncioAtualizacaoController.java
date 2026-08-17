package br.com.topsdojob.v3.web.admin.anuncio;

import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.AdminAnuncioProprietarioAtualizacaoService;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.anuncio.dto.AdminAnuncioProprietarioAtualizacaoRequest;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.usuario.AdminUsuarioAtualizacaoException;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioAtualizacaoErroDto;
import br.com.topsdojob.v3.application.admin.usuario.dto.AdminUsuarioDetalheDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioAtualizacaoController {

    private final AdminAnuncioAtualizacaoService service;
    private final AdminAnuncioProprietarioAtualizacaoService proprietarioService;

    public AdminAnuncioAtualizacaoController(
            AdminAnuncioAtualizacaoService service,
            AdminAnuncioProprietarioAtualizacaoService proprietarioService) {
        this.service = service;
        this.proprietarioService = proprietarioService;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminAnuncioDetalheDto> atualizar(
            @PathVariable UUID id,
            @RequestBody AdminAnuncioAtualizacaoRequest body,
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

    @PatchMapping("/{id}/proprietario")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
    public ResponseEntity<AdminUsuarioDetalheDto> atualizarProprietario(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminAnuncioProprietarioAtualizacaoRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(proprietarioService.atualizar(
                        id,
                        body,
                        administrador,
                        RequestIdContext.current(request)));
    }

    @ExceptionHandler(AdminUsuarioAtualizacaoException.class)
    public ResponseEntity<AdminUsuarioAtualizacaoErroDto> handleAtualizacaoProprietario(
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
}
