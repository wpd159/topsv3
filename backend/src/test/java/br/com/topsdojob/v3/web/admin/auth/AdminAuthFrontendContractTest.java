package br.com.topsdojob.v3.web.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAuthFrontendContractTest {

    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    @Test
    void loginAdministrativoNavegavelUsaEndpointsReaisComCookieECsrf() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "admin-auth-api.ts")));
        String loginPage = Files.readString(FRONTEND.resolve(Path.of(
                "app", "(admin-auth)", "admin", "login", "page.tsx")));

        assertThat(adapter)
                .contains("/api/admin/auth")
                .contains("'/login'")
                .contains("'/me'")
                .contains("'/logout'")
                .contains("credentials: 'include'")
                .contains("antiForgeryHeaderName()")
                .doesNotContain("/api/public/auth/login");
        assertThat(loginPage)
                .contains("loginAdmin")
                .contains("/admin/stories")
                .doesNotContain("fetch(");
    }

    @Test
    void guardRedirecionaSemSessaoELogoutUsaContratoAdmin() throws Exception {
        String layout = Files.readString(FRONTEND.resolve(Path.of(
                "app", "(painel-admin)", "admin", "layout.tsx")));
        String authContext = Files.readString(FRONTEND.resolve(Path.of("context", "AuthContext.tsx")));

        assertThat(layout).contains("router.replace(\"/admin/login\")");
        assertThat(authContext)
                .contains("logoutAdmin")
                .contains("window.location.pathname.startsWith('/admin')");
    }

    @Test
    void meAnonimoInicializaCsrfSemExporSessao() throws Exception {
        Path backend = Path.of("src", "main", "java", "br", "com", "topsdojob", "v3");
        String controller = Files.readString(backend.resolve(Path.of(
                "web", "admin", "auth", "AdminAuthController.java")));
        String security = Files.readString(backend.resolve(Path.of(
                "security", "config", "SecurityConfig.java")));

        assertThat(controller)
                .contains("CsrfToken csrfToken")
                .contains("if (csrfToken != null)")
                .contains("csrfToken.getToken()")
                .contains("authenticationService.me(authentication)");
        assertThat(security)
                .contains(".requestMatchers(HttpMethod.GET, \"/api/admin/auth/me\").permitAll()")
                .contains(".requestMatchers(\"/api/admin/**\").authenticated()");
    }
}
