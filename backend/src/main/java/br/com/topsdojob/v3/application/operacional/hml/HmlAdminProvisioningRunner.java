package br.com.topsdojob.v3.application.operacional.hml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("homologacao")
@ConditionalOnProperty(name = "app.hml-admin-provision.enabled", havingValue = "true")
public class HmlAdminProvisioningRunner implements ApplicationRunner {

    private final String email;
    private final HmlAdminProvisioningService service;

    public HmlAdminProvisioningRunner(
            @Value("${HML_ADMIN_PROVISION_EMAIL:}") String email,
            HmlAdminProvisioningService service) {
        this.email = email;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String runtimeValue = lerCredencial();
        try {
            HmlAdminProvisioningService.ProvisioningResult result = service.provisionar(email, runtimeValue);
            String status = result.usuarioCriado() ? "CRIADO" : "ATUALIZADO";
            System.out.println("HML_ADMIN_PROVISION_RESULT=" + status);
        } finally {
            runtimeValue = null;
        }
    }

    private String lerCredencial() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String value = reader.readLine();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("credencial de runtime obrigatoria via stdin");
        }
        return value;
    }
}
