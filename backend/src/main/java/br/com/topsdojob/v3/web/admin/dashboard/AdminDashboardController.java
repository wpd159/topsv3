package br.com.topsdojob.v3.web.admin.dashboard;

import br.com.topsdojob.v3.application.admin.dashboard.AdminDashboardHojeService;
import br.com.topsdojob.v3.application.admin.dashboard.dto.AdminDashboardHojeDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardHojeService service;

    public AdminDashboardController(AdminDashboardHojeService service) {
        this.service = service;
    }

    @GetMapping("/hoje")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ANUNCIO_LER')")
    public ResponseEntity<AdminDashboardHojeDto> hoje() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.consultar());
    }
}
