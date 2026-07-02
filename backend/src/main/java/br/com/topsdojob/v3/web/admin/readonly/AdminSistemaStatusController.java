package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminSistemaStatusService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminStatusSistemaDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sistema/status")
public class AdminSistemaStatusController {

    private final AdminSistemaStatusService service;

    public AdminSistemaStatusController(AdminSistemaStatusService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') and hasAnyAuthority('ADMIN_CONFIGURAR','SEGURANCA_GERENCIAR')")
    public AdminStatusSistemaDto consultar() {
        return service.consultar();
    }
}
