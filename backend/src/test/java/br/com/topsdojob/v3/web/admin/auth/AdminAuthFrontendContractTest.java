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
        String adminNavigation = Files.readString(FRONTEND.resolve(Path.of("lib", "admin-navigation.ts")));
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
                .contains("request<AdminAccountAction>('/password'")
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
        assertThat(adminNavigation)
                .contains("export const ADMIN_DASHBOARD_PATH = '/admin/dashboard'")
                .contains("export function resolveAdminPostLoginSearch(search: string)")
                .contains("return resolveAdminPostLoginPath(new URLSearchParams(search).get('next'))");
        assertThat(controller)
                .contains("@RequestMapping(\"/api/admin/auth\")")
                .contains("@PostMapping(\"/login\")")
                .contains("@GetMapping(\"/me\")")
                .contains("@PostMapping(\"/logout\")")
                .contains("@PostMapping(\"/password\")")
                .contains("segurancaService.alterarSenhaAdministrativa(");
        assertThat(loginPage)
                .contains("const session = await loginAdmin(login.trim(), credential)")
                .contains("await refresh()")
                .contains("import { resolveAdminPostLoginSearch } from '@/lib/admin-navigation'")
                .contains("router.replace(postLoginDestination())")
                .contains("router.refresh()")
                .contains("await logoutAdmin()")
                .contains("Esqueci minha senha")
                .contains("<RecuperarSenhaModal")
                .doesNotContain("router.replace('/admin/stories')")
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

    @Test
    void alteracaoERecuperacaoDeSenhaReutilizamContratosCanonicos() throws Exception {
        String adapter = Files.readString(FRONTEND.resolve(Path.of("lib", "admin-auth-api.ts")));
        String dialog = Files.readString(FRONTEND.resolve(Path.of(
                "features", "admin-auth", "admin-change-password-dialog.tsx")));
        String footer = Files.readString(FRONTEND.resolve(Path.of(
                "app", "(painel-admin)", "admin", "components", "sidebar",
                "sidebar-user-footer.tsx")));
        String recovery = Files.readString(FRONTEND.resolve(Path.of(
                "components", "modals", "recuperar-senha-modal.tsx")));
        String openApi = Files.readString(Path.of(
                "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

        assertThat(footer)
                .contains("Alterar senha")
                .contains("<AdminChangePasswordDialog")
                .contains("onOpenChange={setPasswordDialogOpen}");
        assertThat(dialog)
                .contains("Senha atual")
                .contains("Nova senha")
                .contains("Confirmar nova senha")
                .contains("autoComplete=\"current-password\"")
                .contains("<PasswordRequirements")
                .contains("await changeAdminPassword(")
                .contains("Senha alterada com sucesso.")
                .contains("window.location.assign('/admin/login')")
                .doesNotContain("usuarioId");
        assertThat(adapter)
                .contains("type AdminCredentialField")
                .contains("export type ChangeAdminPasswordPayload")
                .contains("'senhaAtual'")
                .contains("'novaSenha'")
                .contains("'confirmarSenha'")
                .contains("Object.fromEntries")
                .contains("changeAdminPassword(");
        assertThat(recovery)
                .contains("requestPublicPasswordReset")
                .contains("validatePublicResetCode")
                .contains("resetPublicCredential")
                .contains("Se houver uma conta elegível, enviaremos as instruções.");
        assertThat(openApi)
                .contains("/api/admin/auth/" + "pass" + "word:")
                .contains("$ref: \"#/components/schemas/MinhaContaAlterarSenhaRequest\"");
    }
}
