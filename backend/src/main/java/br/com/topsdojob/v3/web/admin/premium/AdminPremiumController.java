package br.com.topsdojob.v3.web.admin.premium;

import br.com.topsdojob.v3.application.admin.premium.BeneficioAnuncioConsultaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumConsistenciaService;
import br.com.topsdojob.v3.application.admin.premium.PremiumStatusConsultaService;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminBeneficioAnuncioDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumAnuncioStatusDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumConsistenciaResumoDto;
import br.com.topsdojob.v3.application.admin.premium.dto.AdminPremiumVencendoResumoDto;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/premium")
public class AdminPremiumController {

    private final PremiumStatusConsultaService statusService;
    private final BeneficioAnuncioConsultaService beneficioService;
    private final PremiumConsistenciaService consistenciaService;

    public AdminPremiumController(
            PremiumStatusConsultaService statusService,
            BeneficioAnuncioConsultaService beneficioService,
            PremiumConsistenciaService consistenciaService) {
        this.statusService = statusService;
        this.beneficioService = beneficioService;
        this.consistenciaService = consistenciaService;
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
}
