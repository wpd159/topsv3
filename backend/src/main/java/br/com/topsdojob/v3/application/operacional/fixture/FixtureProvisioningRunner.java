package br.com.topsdojob.v3.application.operacional.fixture;

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
@ConditionalOnExpression("${app.fixture.admin-provision.enabled:false}"
        + " || ${app.fixture.stories.enabled:false}"
        + " || ${app.fixture.owner-credential.enabled:false}"
        + " || ${app.fixture.auth-smoke.enabled:false}")
public class FixtureProvisioningRunner implements ApplicationRunner {

    private final String email;
    private final boolean adminEnabled;
    private final boolean fixtureEnabled;
    private final boolean fixtureOwnerCredentialEnabled;
    private final boolean authSmokeEnabled;
    private final String authSmokeRuntimeValue;
    private final AdminFixtureProvisioningService service;
    private final StoriesFixtureService fixtureService;
    private final AuthSmokeFixtureService authSmokeFixtureService;
    private final ConfigurableApplicationContext applicationContext;
    private final CredentialReader credentialReader;

    @Autowired
    public FixtureProvisioningRunner(
            @Value("${FIXTURE_ADMIN_PROVISION_EMAIL:}") String email,
            @Value("${app.fixture.admin-provision.enabled:false}") boolean adminEnabled,
            @Value("${app.fixture.stories.enabled:false}") boolean fixtureEnabled,
            @Value("${app.fixture.owner-credential.enabled:false}") boolean fixtureOwnerCredentialEnabled,
            @Value("${app.fixture.auth-smoke.enabled:false}") boolean authSmokeEnabled,
            @Value("${FIXTURE_AUTH_SMOKE_RUNTIME_VALUE:}") String authSmokeRuntimeValue,
            AdminFixtureProvisioningService service,
            StoriesFixtureService fixtureService,
            AuthSmokeFixtureService authSmokeFixtureService,
            ConfigurableApplicationContext applicationContext) {
        this(
                email,
                adminEnabled,
                fixtureEnabled,
                fixtureOwnerCredentialEnabled,
                authSmokeEnabled,
                authSmokeRuntimeValue,
                service,
                fixtureService,
                authSmokeFixtureService,
                applicationContext,
                FixtureProvisioningRunner::lerCredencialStdin);
    }

    FixtureProvisioningRunner(
            String email,
            boolean adminEnabled,
            boolean fixtureEnabled,
            boolean fixtureOwnerCredentialEnabled,
            boolean authSmokeEnabled,
            String authSmokeRuntimeValue,
            AdminFixtureProvisioningService service,
            StoriesFixtureService fixtureService,
            AuthSmokeFixtureService authSmokeFixtureService,
            ConfigurableApplicationContext applicationContext,
            CredentialReader credentialReader) {
        this.email = email;
        this.adminEnabled = adminEnabled;
        this.fixtureEnabled = fixtureEnabled;
        this.fixtureOwnerCredentialEnabled = fixtureOwnerCredentialEnabled;
        this.authSmokeEnabled = authSmokeEnabled;
        this.authSmokeRuntimeValue = authSmokeRuntimeValue;
        this.service = service;
        this.fixtureService = fixtureService;
        this.authSmokeFixtureService = authSmokeFixtureService;
        this.applicationContext = applicationContext;
        this.credentialReader = credentialReader;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String runtimeValue = null;
        try {
            int credentialActions = (adminEnabled ? 1 : 0)
                    + (fixtureOwnerCredentialEnabled ? 1 : 0)
                    + (authSmokeEnabled ? 1 : 0);
            if (credentialActions > 1) {
                throw new IllegalArgumentException("habilite somente uma acao de credencial por execucao");
            }
            if (authSmokeEnabled && fixtureEnabled) {
                throw new IllegalArgumentException("fixture Auth deve executar isoladamente");
            }
            if (adminEnabled) {
                runtimeValue = credentialReader.read();
                AdminFixtureProvisioningService.ProvisioningResult result = service.provisionar(email, runtimeValue);
                String status = result.usuarioCriado() ? "CRIADO" : "ATUALIZADO";
                System.out.println("FIXTURE_ADMIN_PROVISION_RESULT=" + status);
            }
            if (fixtureEnabled) {
                StoriesFixtureService.FixtureResult fixture = fixtureService.provisionar();
                System.out.println("STORIES_FIXTURE_RESULT="
                        + fixture.categoriasCriadas() + ":"
                        + fixture.localidadesCriadas() + ":"
                        + fixture.localizacoesCriadas() + ":"
                        + fixture.anunciosCriados() + ":"
                        + fixture.arquivosCriados() + ":"
                        + fixture.vinculosCriados() + ":"
                        + fixture.beneficiosCriados() + ":"
                        + (fixture.storyCriado() ? "CRIADO" : "PRESERVADO"));
            }
            if (fixtureOwnerCredentialEnabled) {
                runtimeValue = credentialReader.read();
                StoriesFixtureService.FixtureOwnerCredentialResult result =
                        fixtureService.provisionarCredencialProprietario(runtimeValue);
                System.out.println("FIXTURE_OWNER_CREDENTIAL_RESULT=" + result.status());
            }
            if (authSmokeEnabled) {
                runtimeValue = authSmokeRuntimeValue;
                AuthSmokeFixtureService.FixtureResult result =
                        authSmokeFixtureService.reconciliar(runtimeValue);
                System.out.println("AUTH_SMOKE_FIXTURE_RESULT="
                        + result.contasCriadas() + ":"
                        + result.contasAtualizadas() + ":"
                        + result.contasPreservadas());
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
