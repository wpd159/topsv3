package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioDetalhadoConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioOrdenacao;
import br.com.topsdojob.v3.application.admin.readonly.AdminAnuncioSituacao;
import br.com.topsdojob.v3.application.admin.documento.dto.AdminKycEnvioDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioDetalheDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminAnuncioListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminMidiaListaItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminModeracaoHistoricoItemDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminPaginaDto;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminLocalidadeFiltroDto;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<AdminPaginaDto<AdminAnuncioListaItemDto>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "PENDENTES_MODERACAO") AdminAnuncioSituacao situacao,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String bairro,
            @RequestParam(required = false) String termo,
            @RequestParam(defaultValue = "MAIS_RECENTES") AdminAnuncioOrdenacao ordenacao) {
        return semCache(service.listar(
                page,
                size,
                situacao,
                uf,
                cidade,
                bairro,
                termo,
                ordenacao,
                false));
    }

    @GetMapping("/filtros/localidades")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<List<AdminLocalidadeFiltroDto>> localidadesFiltro() {
        return semCache(service.localidadesFiltro());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<AdminAnuncioDetalheDto> detalhar(@PathVariable UUID id) {
        return semCache(service.detalhar(id, false));
    }

    @GetMapping("/{id}/midias")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('MIDIA_REVISAR')")
    public AdminPaginaDto<AdminMidiaListaItemDto> midias(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarMidiasDoAnuncio(id, page, size);
    }

    @GetMapping("/{id}/documentos")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('DOCUMENTO_REVISAR')")
    public ResponseEntity<List<AdminKycEnvioDto>> documentos(@PathVariable UUID id) {
        return semCache(service.documentosDoAnunciante(id));
    }

    @GetMapping("/{id}/historico-moderacao")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and (hasAuthority('ANUNCIO_MODERAR') or hasAuthority('MIDIA_REVISAR'))")
    public List<AdminModeracaoHistoricoItemDto> historicoModeracao(@PathVariable UUID id) {
        return service.historico(id);
    }

    private <T> ResponseEntity<T> semCache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
