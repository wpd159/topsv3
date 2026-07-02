package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminModeracaoDetalhadaConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminRevisaoListaItemDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusRevisaoAnuncio;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoRevisaoAnuncio;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/moderacao/revisoes")
public class AdminModeracaoDetalhadaController {

    private final AdminModeracaoDetalhadaConsultaService service;

    public AdminModeracaoDetalhadaController(AdminModeracaoDetalhadaConsultaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminPaginaDto<AdminRevisaoListaItemDto> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StatusRevisaoAnuncio status,
            @RequestParam(required = false) TipoRevisaoAnuncio tipo,
            @RequestParam(required = false) UUID anuncioId) {
        return service.listar(page, size, status, tipo, anuncioId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_MODERAR')")
    public AdminRevisaoDetalheDto detalhar(@PathVariable UUID id) {
        return service.detalhar(id);
    }
}
