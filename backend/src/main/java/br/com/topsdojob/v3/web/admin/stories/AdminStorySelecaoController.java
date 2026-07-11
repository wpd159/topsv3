package br.com.topsdojob.v3.web.admin.stories;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.stories.AdminStorySelecaoService;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStoryCandidatoDto;
import br.com.topsdojob.v3.application.admin.stories.dto.AdminStorySelecaoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorySelecaoController {

    private final AdminStorySelecaoService service;

    public AdminStorySelecaoController(AdminStorySelecaoService service) {
        this.service = service;
    }

    @GetMapping("/selecao")
    public AdminStorySelecaoDto consultar() {
        return service.consultar();
    }

    @GetMapping("/candidatos")
    public AdminPaginaDto<AdminStoryCandidatoDto> candidatos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String termo) {
        return service.listarCandidatos(page, size, termo);
    }

    @PostMapping("/selecao/{anuncioId}")
    public AdminStorySelecaoDto ativar(
            @PathVariable UUID anuncioId,
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return service.ativar(anuncioId, ator, RequestIdContext.current(request));
    }

    @DeleteMapping("/selecao")
    public AdminStorySelecaoDto desativar(
            @AuthenticationPrincipal AdminUserPrincipal ator,
            HttpServletRequest request) {
        return service.desativar(ator, RequestIdContext.current(request));
    }
}
