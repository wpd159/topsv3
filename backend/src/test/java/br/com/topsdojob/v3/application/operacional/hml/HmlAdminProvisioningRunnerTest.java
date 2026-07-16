package br.com.topsdojob.v3.application.operacional.hml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.UUID;

@ExtendWith(OutputCaptureExtension.class)
class HmlAdminProvisioningRunnerTest {

    @Test
    void permaneceRestritoAoProfileHomologacaoEComFlagDesabilitadaPorPadrao() {
        Profile profile = HmlAdminProvisioningRunner.class.getAnnotation(Profile.class);
        ConditionalOnExpression condition =
                HmlAdminProvisioningRunner.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(profile.value()).containsExactly("homologacao");
        assertThat(condition.value()).contains("${app.hml-auth-smoke.enabled:false}");
    }

    @Test
    void executaSomenteFixtureSemLerOuAlterarCredencialAdmin() throws Exception {
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(fixtureService.provisionar()).thenReturn(
                new HmlStoriesFixtureService.FixtureResult(0, 0, 0, 0, 0, 0, 0, false));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                true,
                false,
                false,
                "",
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> {
                    throw new AssertionError("fixture nao pode ler credencial ADMIN");
                });

        runner.run(mock(ApplicationArguments.class));

        verify(fixtureService).provisionar();
        verify(adminService, never()).provisionar(any(), any());
        verify(context).close();
    }

    @Test
    void provisionamentoAdminContinuaExigindoCredencialSemExecutarFixtureImplicitamente() throws Exception {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(adminService.provisionar("admin.hml@example.invalid", runtimeValue))
                .thenReturn(new HmlAdminProvisioningService.ProvisioningResult(false, true));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                true,
                false,
                false,
                false,
                "",
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> runtimeValue);

        runner.run(mock(ApplicationArguments.class));

        verify(adminService).provisionar("admin.hml@example.invalid", runtimeValue);
        verify(fixtureService, never()).provisionar();
        verify(context).close();
    }

    @Test
    void provisionaCredencialDoProprietarioSemAlterarAdminOuReconciliarFixture() throws Exception {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(fixtureService.provisionarCredencialProprietario(runtimeValue))
                .thenReturn(new HmlStoriesFixtureService.FixtureOwnerCredentialResult(
                        HmlStoriesFixtureService.FixtureOwnerCredentialStatus.ATUALIZADA));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                false,
                true,
                false,
                "",
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> runtimeValue);

        runner.run(mock(ApplicationArguments.class));

        verify(fixtureService).provisionarCredencialProprietario(runtimeValue);
        verify(fixtureService, never()).provisionar();
        verify(adminService, never()).provisionar(any(), any());
        verify(context).close();
    }

    @Test
    void recusaAcoesConcorrentesDeCredencial() {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                true,
                false,
                true,
                false,
                "",
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> runtimeValue);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("somente uma acao de credencial");

        verify(adminService, never()).provisionar(any(), any());
        verify(fixtureService, never()).provisionarCredencialProprietario(any());
        verify(context).close();
    }

    @Test
    void executaFixtureAuthComSegredoDeAmbienteSemLerStdinOuExporValor(CapturedOutput output)
            throws Exception {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(authFixtureService.reconciliar(runtimeValue))
                .thenReturn(new HmlAuthSmokeFixtureService.FixtureResult(3, 0, 0));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                false,
                false,
                true,
                runtimeValue,
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> {
                    throw new AssertionError("fixture Auth nao pode ler stdin");
                });

        runner.run(mock(ApplicationArguments.class));

        verify(authFixtureService).reconciliar(runtimeValue);
        verify(adminService, never()).provisionar(any(), any());
        verify(fixtureService, never()).provisionar();
        verify(context).close();
        assertThat(output).contains("HML_AUTH_SMOKE_FIXTURE_RESULT=3:0:0");
        assertThat(output).doesNotContain(runtimeValue);
    }

    @Test
    void recusaFixtureAuthComFixtureDeStoriesNaMesmaExecucao() {
        String runtimeValue = "Aa1!" + UUID.randomUUID();
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        HmlAuthSmokeFixtureService authFixtureService = mock(HmlAuthSmokeFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                true,
                false,
                true,
                runtimeValue,
                adminService,
                fixtureService,
                authFixtureService,
                context,
                () -> runtimeValue);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("isoladamente");

        verify(authFixtureService, never()).reconciliar(any());
        verify(fixtureService, never()).provisionar();
        verify(context).close();
    }
}
