package br.com.topsdojob.v3.application.operacional.hml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

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
        HmlAdminProvisioningService adminService = mock(HmlAdminProvisioningService.class);
        HmlStoriesFixtureService fixtureService = mock(HmlStoriesFixtureService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(adminService.provisionar("admin.hml@example.invalid", "valor-runtime-seguro"))
                .thenReturn(new HmlAdminProvisioningService.ProvisioningResult(false, true));
        HmlAdminProvisioningRunner runner = new HmlAdminProvisioningRunner(
                "admin.hml@example.invalid",
                true,
                false,
                adminService,
                fixtureService,
                context,
                () -> "valor-runtime-seguro");

        runner.run(mock(ApplicationArguments.class));

        verify(adminService).provisionar("admin.hml@example.invalid", "valor-runtime-seguro");
        verify(fixtureService, never()).provisionar();
        verify(context).close();
    }
}
