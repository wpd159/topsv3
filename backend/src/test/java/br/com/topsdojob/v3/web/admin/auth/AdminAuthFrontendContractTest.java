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
        String apiContract = Files.readString(FRONTEND.resolve(Path.of("lib", "api-contract.ts")));
        String loginPage = Files.readString(FRONTEND.resolve(Path.of(
                "app", "(admin-auth)", "admin", "login", "page.tsx")));
        String controller = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3",
                "web", "admin", "auth", "AdminAuthController.java"));

        assertThat(adapter)
                .contains("return adminApiUrl(`/auth${path}`)")
                .contains("fetch(adminAuthUrl('/me')")
                .contains("fetch(adminAuthUrl(path)")
                .contains("request<AdminSession>('/login'")
                .contains("request<AdminSession>('/me')")
                .contains("request<{ autenticado: boolean; status: string }>('/logout'")
                .contains("credentials: 'include'")
                .contains("if (!['GET', 'HEAD', 'OPTIONS'].includes(method))")
                .contains("const antiForgeryValue = await ensureAntiForgeryValue()")
                .contains("headers.set(antiForgeryHeaderName(), antiForgeryValue)")
                .doesNotContain("/api/admin")
                .doesNotContain("/api/public/api/admin")
                .doesNotContain("/api/public/auth/login");
        assertThat(countOccurrences(adapter, "fetch("))
                .isEqualTo(countOccurrences(adapter, "fetch(adminAuthUrl("))
                .isEqualTo(2);
        assertThat(apiContract)
                .contains("export function adminApiUrl(path: string)")
                .contains("normalized === '/api/admin' || normalized.startsWith('/api/admin/')")
                .contains("return `${backendApiRoot()}/api/admin${normalized}`");
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/auth\")")
                .contains("@PostMapping(\"/login\")")
                .contains("@GetMapping(\"/me\")")
                .contains("@PostMapping(\"/logout\")");
        assertThat(loginPage)
                .contains("const session = await loginAdmin(login.trim(), credential)")
                .contains("await refresh()")
                .contains("router.replace('/admin/stories')")
                .contains("router.refresh()")
                .contains("await logoutAdmin()")
                .doesNotContain("fetch(");
    }

    private static int countOccurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
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
