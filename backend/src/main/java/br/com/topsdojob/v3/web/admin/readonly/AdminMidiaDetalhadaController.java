package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminMidiaDetalhadaConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.ClassificacaoConteudo;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.StatusAnuncioMidia;
import br.com.topsdojob.v3.persistence.shared.PersistenceEnums.TipoAnuncioMidia;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/midias")
public class AdminMidiaDetalhadaController {

    private final AdminMidiaDetalhadaConsultaService service;

    public AdminMidiaDetalhadaController(AdminMidiaDetalhadaConsultaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminPaginaDto<AdminMidiaListaItemDto> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) StatusAnuncioMidia status,
            @RequestParam(required = false) ClassificacaoConteudo classificacaoConteudo,
            @RequestParam(required = false) TipoAnuncioMidia tipo,
            @RequestParam(required = false) UUID anuncioId) {
        return service.listar(page, size, status, classificacaoConteudo, tipo, anuncioId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminMidiaDetalheDto detalhar(@PathVariable UUID id) {
        return service.detalhar(id);
    }
}
