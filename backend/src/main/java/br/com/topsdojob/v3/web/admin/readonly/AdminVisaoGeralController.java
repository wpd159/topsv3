package br.com.topsdojob.v3.web.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.AdminVisaoGeralConsultaService;
import br.com.topsdojob.v3.application.admin.readonly.dto.AdminVisaoGeralDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/visao-geral")
public class AdminVisaoGeralController {

    private final AdminVisaoGeralConsultaService service;

    public AdminVisaoGeralController(AdminVisaoGeralConsultaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MODERADOR')")
    public AdminVisaoGeralDto consultar(Authentication authentication) {
        return service.consultar(
                hasRole(authentication, "ROLE_ADMIN"),
                hasRole(authentication, "ROLE_MODERADOR"),
                false);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
