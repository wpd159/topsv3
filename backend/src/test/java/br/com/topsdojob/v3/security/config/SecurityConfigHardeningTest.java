package br.com.topsdojob.v3.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SecurityConfigHardeningTest {

    @Test
    void appEnvSemLocalExplicitoNaoDesabilitaCsrf() {
        SecurityConfig semEnvLocal = new SecurityConfig("nao_configurado", mock(AdminSecurityErrorWriter.class));
        SecurityConfig local = new SecurityConfig("local", mock(AdminSecurityErrorWriter.class));

        assertThat(semEnvLocal.csrfDisabledOnlyInLocal()).isFalse();
        assertThat(local.csrfDisabledOnlyInLocal()).isTrue();
    }

    @Test
    void applicationBaseUsaDefaultsFailClosed() throws Exception {
        String base = Files.readString(Path.of("src", "main", "resources", "application.yml"));
        String local = Files.readString(Path.of("src", "main", "resources", "application-local.yml"));

        assertThat(base).contains("env: ${APP_ENV:nao_configurado}");
        assertThat(base).doesNotContain("env: ${APP_ENV:local}");
        assertThat(base).contains("secure: ${APP_ADMIN_SESSION_COOKIE_SECURE:true}");
        assertThat(base).contains("enabled: ${EFI_ENABLED:false}");
        assertThat(base).doesNotContain("mock-mode");
        assertThat(local).contains("env: ${APP_ENV:local}");
        assertThat(local).contains("secure: ${APP_ADMIN_SESSION_COOKIE_SECURE:false}");
        assertThat(local).contains("enabled: false");
        assertThat(local).doesNotContain("mock-mode");
    }

    @Test
    void securityConfigBloqueiaApiDesconhecidaPorPadrao() throws Exception {
        String config = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "security",
                "config",
                "SecurityConfig.java"));

        assertThat(config).contains(".requestMatchers(\"/api/**\").denyAll()");
        assertThat(config).doesNotContain(".anyRequest().permitAll()");
    }

    @Test
    void securityConfigProtegeEndpointsAdminReadonlyPorPapel() throws Exception {
        String config = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "security",
                "config",
                "SecurityConfig.java"));

        assertThat(config)
                .contains("/api/admin/visao-geral")
                .contains("/api/admin/anuncios/resumo")
                .contains("/api/admin/moderacao/resumo")
                .contains("/api/admin/midias/resumo")
                .contains("/api/admin/metricas/resumo")
                .contains("/api/admin/sistema/status")
                .contains("/api/admin/anuncios")
                .contains("/api/admin/anuncios/*")
                .contains("/api/admin/premium/**")
                .contains("/api/admin/creditos/**")
                .contains("/api/admin/pagamentos/**")
                .contains("/api/admin/desempenho/resumo")
                .contains("/api/admin/desempenho/anunciantes/*")
                .contains("/api/admin/desempenho/anuncios/*")
                .contains("/api/admin/desempenho/anuncios/*/diario")
                .contains("/api/admin/desempenho/anuncios/*/origens")
                .contains("/api/admin/anuncios/*/remeter-revisao")
                .contains("/api/admin/anuncios/*/midias")
                .contains("/api/admin/midias")
                .contains("/api/admin/moderacao/revisoes")
                .contains("/api/admin/moderacao/revisoes/*/decidir")
                .contains("/api/admin/outbox")
                .contains("/api/admin/outbox/*")
                .contains("/api/admin/outbox/*/preview")
                .contains("/api/admin/outbox/*/simular-processamento-local")
                .contains("/api/admin/midias/*/decidir")
                .contains(".hasAnyRole(\"ADMIN\", \"MODERADOR\")")
                .contains(".hasRole(\"ADMIN\")")
                .doesNotContain("\"COMERCIAL\"");
    }

    @Test
    void csrfHomologacaoAceitaValorCruDoCookieEnviadoNoHeaderSpa() {
        SecurityConfig config = new SecurityConfig("homologacao", mock(AdminSecurityErrorWriter.class));
        String headerName = String.join("-", "X", "XSRF", "TOKEN");
        String requestValue = String.join("-", "csrf", "sintetico", "local");
        DefaultCsrfToken csrfContract = new DefaultCsrfToken(headerName, "_csrf", requestValue);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(headerName, requestValue);

        String resolved = config.csrfTokenRequestHandler().resolveCsrfTokenValue(request, csrfContract);

        assertThat(resolved).isEqualTo(requestValue);
    }
}
