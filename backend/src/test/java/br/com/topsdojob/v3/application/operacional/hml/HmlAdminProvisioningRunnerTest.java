package br.com.topsdojob.v3.application.operacional.hml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.UUID;

class HmlAdminProvisioningRunnerTest {

    @Test
    void executaSomenteFixtureSemLerOuAlterarCredencialAdmin() throws Exception {
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(fixtureService.provisionar()).thenReturn(
                new HmlStoriesFixtureService.FixtureResult(0, 0, 0, 0, 0, 0, 0, false));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                true,
                false,
                adminService,
                fixtureService,
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
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(adminService.provisionar("admin.hml@example.invalid", runtimeValue))
                .thenReturn(new HmlAdminProvisioningService.ProvisioningResult(false, true));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                true,
                false,
                false,
                adminService,
                fixtureService,
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
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(fixtureService.provisionarCredencialProprietario(runtimeValue))
                .thenReturn(new HmlStoriesFixtureService.FixtureOwnerCredentialResult(
                        HmlStoriesFixtureService.FixtureOwnerCredentialStatus.ATUALIZADA));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                false,
                false,
                true,
                adminService,
                fixtureService,
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
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                true,
                false,
                true,
                adminService,
                fixtureService,
                context,
                () -> runtimeValue);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("somente uma acao de credencial");

        verify(adminService, never()).provisionar(any(), any());
        verify(fixtureService, never()).provisionarCredencialProprietario(any());
        verify(context).close();
    }
}
