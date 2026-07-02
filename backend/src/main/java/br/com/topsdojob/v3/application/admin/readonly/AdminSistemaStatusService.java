package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminStatusSistemaDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminSistemaStatusService {

    private final String appEnv;
    private final boolean efiPixMockMode;

    public AdminSistemaStatusService(
            @Value("${app.env:nao_configurado}") String appEnv,
            @Value("${efi.pix.mock-mode:false}") boolean efiPixMockMode) {
        this.appEnv = appEnv == null ? "nao_configurado" : appEnv.trim();
        this.efiPixMockMode = efiPixMockMode;
    }

    public AdminStatusSistemaDto consultar() {
        boolean local = "local".equalsIgnoreCase(appEnv);
        return new AdminStatusSistemaDto(
                "topsdojob-v3-backend",
                appEnv,
                local,
                efiPixMockMode,
                "API_FAIL_CLOSED",
                "PENDENTE_CSRF_ADMIN_PRODUCAO");
    }
}
