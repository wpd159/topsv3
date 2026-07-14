package br.com.topsdojob.v3.application.admin.readonly;

import br.com.topsdojob.v3.application.admin.readonly.dto.AdminStatusSistemaDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminSistemaStatusService {

    private final String appEnv;
    private final boolean efiPixEnabled;

    public AdminSistemaStatusService(
            @Value("${app.env:nao_configurado}") String appEnv,
            @Value("${efi.pix.enabled:false}") boolean efiPixEnabled) {
        this.appEnv = appEnv == null ? "nao_configurado" : appEnv.trim();
        this.efiPixEnabled = efiPixEnabled;
    }

    public AdminStatusSistemaDto consultar() {
        boolean local = "local".equalsIgnoreCase(appEnv);
        return new AdminStatusSistemaDto(
                "topsdojob-v3-backend",
                appEnv,
                local,
                efiPixEnabled,
                "API_FAIL_CLOSED",
                "PENDENTE_CSRF_ADMIN_PRODUCAO");
    }
}
