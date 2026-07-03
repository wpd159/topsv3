package br.com.topsdojob.v3.web.admin.desempenho;

import br.com.topsdojob.v3.application.admin.desempenho.AdminDesempenhoConsultaService;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoAnuncianteDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoAnuncioDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoDiarioDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoOrigemDto;
import br.com.topsdojob.v3.application.admin.desempenho.dto.AdminDesempenhoResumoDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/desempenho")
public class AdminDesempenhoController {

    private final AdminDesempenhoConsultaService consultaService;

    public AdminDesempenhoController(AdminDesempenhoConsultaService consultaService) {
        this.consultaService = consultaService;
    }

    @GetMapping("/anuncios/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public AdminDesempenhoAnuncioDto anuncio(@PathVariable UUID id) {
        return consultaService.consultarAnuncio(id);
    }

    @GetMapping("/anuncios/{id}/diario")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public List<AdminDesempenhoDiarioDto> diario(@PathVariable UUID id) {
        return consultaService.consultarDiario(id);
    }

    @GetMapping("/anuncios/{id}/origens")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public List<AdminDesempenhoOrigemDto> origens(@PathVariable UUID id) {
        return consultaService.consultarOrigens(id);
    }

    @GetMapping("/anunciantes/{usuarioId}")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminDesempenhoAnuncianteDto anunciante(@PathVariable UUID usuarioId) {
        return consultaService.consultarAnunciante(usuarioId);
    }

    @GetMapping("/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminDesempenhoResumoDto resumo() {
        return consultaService.consultarResumo();
    }
}
