package br.com.topsdojob.v3.web.admin.premium;

import br.com.topsdojob.v3.application.admin.premium.AdminPremiumCatalogoService;
import br.com.topsdojob.v3.application.admin.premium.AdminPremiumOperacaoService;
import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumConsistenciaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumStatusConsultaService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminBeneficioAnuncioDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAnuncioStatusDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoOperacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAtivacaoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCancelarRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumCatalogoUpdateRequest;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumVencendoResumoDto;
import br.com.topsdojob.v3.application.premium.PremiumCatalogoService;
import br.com.topsdojob.v3.application.premium.dto.PremiumCatalogoDto;
import br.com.topsdojob.v3.platform.request.RequestIdContext;
import br.com.topsdojob.v3.security.admin.AdminUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/admin/premium")
public class AdminPremiumController {

    private final PremiumStatusConsultaService statusService;
    private final BeneficioAnuncioConsultaService beneficioService;
    private final PremiumConsistenciaService consistenciaService;
    private final PremiumCatalogoService catalogoService;
    private final AdminPremiumCatalogoService catalogoAdminService;
    private final AdminPremiumOperacaoService operacaoService;

    public AdminPremiumController(
            PremiumStatusConsultaService statusService,
            BeneficioAnuncioConsultaService beneficioService,
            PremiumConsistenciaService consistenciaService,
            PremiumCatalogoService catalogoService,
            AdminPremiumCatalogoService catalogoAdminService,
            AdminPremiumOperacaoService operacaoService) {
        this.statusService = statusService;
        this.beneficioService = beneficioService;
        this.consistenciaService = consistenciaService;
        this.catalogoService = catalogoService;
        this.catalogoAdminService = catalogoAdminService;
        this.operacaoService = operacaoService;
    }

    @GetMapping("/anuncios/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminPremiumAnuncioStatusDto status(@PathVariable UUID id) {
        return statusService.consultar(id);
    }

    @GetMapping("/anuncios/{id}/beneficios")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public List<AdminBeneficioAnuncioDto> beneficios(@PathVariable UUID id) {
        return beneficioService.consultar(id);
    }

    @GetMapping("/consistencia")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminPremiumConsistenciaResumoDto consistencia() {
        return consistenciaService.consultarConsistencia();
    }

    @GetMapping("/vencendo")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR','COMERCIAL') and hasAuthority('ANUNCIO_LER')")
    public AdminPremiumVencendoResumoDto vencendo() {
        return consistenciaService.consultarVencendo();
    }

    @GetMapping("/catalogo")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public List<PremiumCatalogoDto> catalogo() {
        return catalogoService.catalogoAdministrativo();
    }

    @PutMapping("/catalogo/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PREMIUM_GERENCIAR')")
    public PremiumCatalogoDto atualizarCatalogo(
            @PathVariable UUID id,
            @RequestBody AdminPremiumCatalogoUpdateRequest body,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return catalogoAdminService.atualizarBeneficio(
                id,
                body,
                administrador,
                RequestIdContext.current(request));
    }

    @PostMapping("/ativacoes/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PREMIUM_GERENCIAR')")
    public AdminPremiumAtivacaoOperacaoDto cancelar(
            @PathVariable UUID id,
            @RequestBody AdminPremiumCancelarRequest body,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AdminUserPrincipal administrador,
            HttpServletRequest request) {
        return operacaoService.cancelar(
                id,
                body == null ? null : body.motivo(),
                idempotencyKey,
                administrador,
                RequestIdContext.current(request));
    }

    @GetMapping("/ativacoes")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('FINANCEIRO_LER')")
    public List<AdminPremiumAtivacaoDto> ativacoes(@RequestParam UUID usuarioId) {
        return operacaoService.listar(usuarioId);
    }
}
