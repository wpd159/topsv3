package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminModeracaoHistoricoItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import java.util.List;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusModeracaoAnuncio;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anuncios")
public class AdminAnuncioDetalhadoController {

    private final AdminAnuncioDetalhadoConsultaService service;

    public AdminAnuncioDetalhadoController(AdminAnuncioDetalhadoConsultaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminPaginaDto<AdminAnuncioListaItemDto> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StatusAnuncio status,
            @RequestParam(required = false) StatusModeracaoAnuncio statusModeracao,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) String termo,
            Authentication authentication) {
        return service.listar(
                page,
                size,
                status,
                statusModeracao,
                uf,
                cidade,
                bairro,
                termo,
                hasRole(authentication, "ROLE_COMERCIAL") && !hasRole(authentication, "ROLE_ADMIN"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminAnuncioDetalheDto detalhar(@PathVariable UUID id, Authentication authentication) {
        return service.detalhar(id, hasRole(authentication, "ROLE_COMERCIAL") && !hasRole(authentication, "ROLE_ADMIN"));
    }

    @GetMapping("/{id}/midias")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminPaginaDto<AdminMidiaListaItemDto> midias(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarMidiasDoAnuncio(id, page, size);
    }

    @GetMapping("/{id}/historico-moderacao")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and (hasAuthority('ANUNCIO_MODERAR') or hasAuthority('MIDIA_REVISAR'))")
    public List<AdminModeracaoHistoricoItemDto> historicoModeracao(@PathVariable UUID id) {
        return service.historico(id);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
