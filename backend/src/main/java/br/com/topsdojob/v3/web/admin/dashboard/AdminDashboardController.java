package br.com.topsdojob.v3.web.admin.dashboard;

import br.com.topsdojob.v3.application.admin.dashboard.AdminDashboardHojeService;
import br.com.topsdojob.v3.application.admin.dashboard.AdminDashboardAnalyticsService;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.Analises;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.DesempenhoDiario;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardAnalyticsDtos.TopWhatsappHoje;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardHojeDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardHojeService service;
    private final AdminDashboardAnalyticsService analyticsService;

    public AdminDashboardController(
            AdminDashboardHojeService service,
            AdminDashboardAnalyticsService analyticsService) {
        this.service = service;
        this.analyticsService = analyticsService;
    }

    @GetMapping("/hoje")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<AdminDashboardHojeDto> hoje() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.consultar());
    }

    @GetMapping("/desempenho-diario")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<DesempenhoDiario> desempenhoDiario(
            @RequestParam(defaultValue = "30") int dias) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(analyticsService.desempenhoDiario(dias));
    }

    @GetMapping("/top-whatsapp-hoje")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<TopWhatsappHoje> topWhatsappHoje(
            @RequestParam(defaultValue = "12") int limite) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(analyticsService.topWhatsappHoje(limite));
    }

    @GetMapping("/analises")
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<Analises> analises(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(analyticsService.analises(admin));
    }
}
