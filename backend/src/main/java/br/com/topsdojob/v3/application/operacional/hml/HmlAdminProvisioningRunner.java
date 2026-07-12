package br.com.topsdojob.v3.application.operacional.hml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("homologacao")
@ConditionalOnProperty(name = "app.hml-admin-provision.enabled", havingValue = "true")
public class HmlAdminProvisioningRunner implements ApplicationRunner {

    private final String email;
    private final HmlAdminProvisioningService service;
    private final HmlStoriesFixtureService fixtureService;
    private final ConfigurableApplicationContext applicationContext;

    public HmlAdminProvisioningRunner(
            @Value("${HML_ADMIN_PROVISION_EMAIL:}") String email,
            HmlAdminProvisioningService service,
            HmlStoriesFixtureService fixtureService,
            ConfigurableApplicationContext applicationContext) {
        this.email = email;
        this.service = service;
        this.fixtureService = fixtureService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String runtimeValue = lerCredencial();
        try {
            HmlAdminProvisioningService.ProvisioningResult result = service.provisionar(email, runtimeValue);
            HmlStoriesFixtureService.FixtureResult fixture = fixtureService.provisionar(runtimeValue);
            String status = result.usuarioCriado() ? "CRIADO" : "ATUALIZADO";
            System.out.println("HML_ADMIN_PROVISION_RESULT=" + status);
            System.out.println("HML_STORIES_FIXTURE_RESULT="
                    + fixture.categoriasCriadas() + ":"
                    + fixture.localidadesCriadas() + ":"
                    + fixture.localizacoesCriadas() + ":"
                    + fixture.anunciosCriados() + ":"
                    + fixture.arquivosCriados() + ":"
                    + fixture.vinculosCriados() + ":"
                    + (fixture.storyCriado() ? "CRIADO" : "PRESERVADO"));
        } finally {
            runtimeValue = null;
            applicationContext.close();
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
