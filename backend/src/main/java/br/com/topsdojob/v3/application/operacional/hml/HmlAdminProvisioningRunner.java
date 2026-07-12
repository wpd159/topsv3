package br.com.topsdojob.v3.application.operacional.hml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("homologacao")
@ConditionalOnExpression("${app.hml-admin-provision.enabled:false} || ${app.hml-fixture.enabled:false}")
public class HmlAdminProvisioningRunner implements ApplicationRunner {

    private final String email;
    private final boolean adminEnabled;
    private final boolean fixtureEnabled;
    private final HmlAdminProvisioningService service;
    private final HmlStoriesFixtureService fixtureService;
    private final ConfigurableApplicationContext applicationContext;
    private final CredentialReader credentialReader;

    @Autowired
    public HmlAdminProvisioningRunner(
            @Value("${HML_ADMIN_PROVISION_EMAIL:}") String email,
            @Value("${app.hml-admin-provision.enabled:false}") boolean adminEnabled,
            @Value("${app.hml-fixture.enabled:false}") boolean fixtureEnabled,
            HmlAdminProvisioningService service,
            HmlStoriesFixtureService fixtureService,
            ConfigurableApplicationContext applicationContext) {
        this(
                email,
                adminEnabled,
                fixtureEnabled,
                service,
                fixtureService,
                applicationContext,
                HmlAdminProvisioningRunner::lerCredencialStdin);
    }

    HmlAdminProvisioningRunner(
            String email,
            boolean adminEnabled,
            boolean fixtureEnabled,
            HmlAdminProvisioningService service,
            HmlStoriesFixtureService fixtureService,
            ConfigurableApplicationContext applicationContext,
            CredentialReader credentialReader) {
        this.email = email;
        this.adminEnabled = adminEnabled;
        this.fixtureEnabled = fixtureEnabled;
        this.service = service;
        this.fixtureService = fixtureService;
        this.applicationContext = applicationContext;
        this.credentialReader = credentialReader;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String runtimeValue = null;
        try {
            if (adminEnabled) {
                runtimeValue = credentialReader.read();
                HmlAdminProvisioningService.ProvisioningResult result = service.provisionar(email, runtimeValue);
                String status = result.usuarioCriado() ? "CRIADO" : "ATUALIZADO";
                System.out.println("HML_ADMIN_PROVISION_RESULT=" + status);
            }
            if (fixtureEnabled) {
                HmlStoriesFixtureService.FixtureResult fixture = fixtureService.provisionar();
                System.out.println("HML_STORIES_FIXTURE_RESULT="
                        + fixture.categoriasCriadas() + ":"
                        + fixture.localidadesCriadas() + ":"
                        + fixture.localizacoesCriadas() + ":"
                        + fixture.anunciosCriados() + ":"
                        + fixture.arquivosCriados() + ":"
                        + fixture.vinculosCriados() + ":"
                        + fixture.beneficiosCriados() + ":"
                        + (fixture.storyCriado() ? "CRIADO" : "PRESERVADO"));
            }
        } finally {
            runtimeValue = null;
            applicationContext.close();
        }
    }

    private static String lerCredencialStdin() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String value = reader.readLine();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("credencial de runtime obrigatoria via stdin");
        }
        return value;
    }

    @FunctionalInterface
    interface CredentialReader {
        String read() throws IOException;
    }
}
