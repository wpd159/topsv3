package br.com.topsdojob.v3.web.publico.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PublicAuthFrontendContractTest {

    private static final Path FRONTEND = Path.of("..", "frontend", "src");

    @Test
    void frontendUsaUmUnicoAdapterPublicoComCookieECsrf() throws Exception {
        Path adapterPath = FRONTEND.resolve(Path.of("lib", "public-auth-api.ts"));
        String adapter = Files.readString(adapterPath);

        assertThat(adapter)
                .contains("'/auth/register'")
                .contains("'/auth/login'")
                .contains("'/auth/me'")
                .contains("'/auth/logout'")
                .contains("updatePublicProfile")
                .contains("method: 'PATCH'")
                .contains("credentials: 'include'")
                .contains("csrfHeaderName()")
                .contains("['X', 'XSRF', 'TOKEN'].join('-')")
                .doesNotContain("checkDuplicidade")
                .doesNotContain("emailExistente")
                .doesNotContain("telefoneExistente")
                .doesNotContain("/api/admin/auth");
        String controller = Files.readString(Path.of(
                "src", "main", "java", "br", "com", "topsdojob", "v3", "web", "publico", "auth",
                "PublicAuthController.java"));
        assertThat(controller)
                .contains("if (csrfToken != null)")
                .contains("csrfToken.getToken()")
                .contains("authSecurity.requireDuplicateLookup(request)")
                .contains("HttpHeaders.RETRY_AFTER");
        assertThat(FRONTEND.resolve(Path.of("features", "auth", "register", "register-api.ts")))
                .doesNotExist();
    }

    @Test
    void loginCadastroESessaoDelegamAoAdapterSemFetchConcorrente() throws Exception {
        String loginModal = Files.readString(FRONTEND.resolve(Path.of("components", "modals", "login-modal.tsx")));
        String registerForm = Files.readString(FRONTEND.resolve(Path.of("components", "auth", "register-form.tsx")));
        String authContext = Files.readString(FRONTEND.resolve(Path.of("context", "AuthContext.tsx")));

        assertThat(loginModal).contains("loginPublic").doesNotContain("fetch(`${API}/auth/login`");
        assertThat(registerForm)
                .contains("@/lib/public-auth-api")
                .contains("submitRegister")
                .doesNotContain("features/auth/register/register-api")
                .doesNotContain("checkDuplicidade")
                .doesNotContain("Este e-mail ja esta em uso")
                .doesNotContain("Este telefone ja esta cadastrado");
        assertThat(authContext).contains("getPublicSession").contains("logoutPublic");
    }

    @Test
    void areaPrivadaUsaSessaoPublicaRealSemEndpointsLegados() throws Exception {
        String middleware = Files.readString(FRONTEND.resolve("middleware.ts"));
        String privateLayout = Files.readString(FRONTEND.resolve(Path.of("app", "(private-routes)", "layout.tsx")));
        String guard = Files.readString(FRONTEND.resolve(Path.of("components", "auth", "private-session-guard.tsx")));
        String painel = Files.readString(FRONTEND.resolve(Path.of("app", "(private-routes)", "painel", "page.tsx")));
        String perfil = Files.readString(FRONTEND.resolve(Path.of("app", "(private-routes)", "minha-conta", "page.tsx")));

        assertThat(middleware)
                .contains("SESSION_COOKIE_NAME = 'JSESSIONID'")
                .contains("url.searchParams.set('login', '1')")
                .doesNotContain("decodeJwtPayload")
                .doesNotContain("access_" + "token");
        assertThat(privateLayout).contains("PrivateSessionGuard");
        assertThat(guard).contains("useAuth").contains("/?login=1&next=");
        assertThat(painel)
                .contains("usuario?.username")
                .contains("await logout()")
                .contains("fetchMinhaMonetizacao")
                .doesNotContain("fetchPainelOverview")
                .doesNotContain("/creditos/planos")
                .doesNotContain("/checkout/creditos");
        assertThat(perfil)
                .contains("updatePublicProfile")
                .contains("await refresh()")
                .doesNotContain("/usuarios/")
                .doesNotContain("/creditos/")
                .doesNotContain("sessionStorage");
        assertThat(FRONTEND.resolve(Path.of("components", "minha-conta", "informacoes-pessoais-card.tsx")))
                .doesNotExist();
        assertThat(FRONTEND.resolve(Path.of("components", "minha-conta", "seguranca-conta-card.tsx")))
                .doesNotExist();
        assertThat(FRONTEND.resolve(Path.of("components", "minha-conta", "two-factor-section.tsx")))
                .doesNotExist();
    }

    @Test
    void footerNaoRenderizaBlocoDeConfiancaRemovido() throws Exception {
        String footer = Files.readString(FRONTEND.resolve(Path.of("components", "layout", "footer.tsx")));

        assertThat(footer)
                .doesNotContain("PILARES_CONFIANCA")
                .doesNotContain("Confiança e segurança")
                .doesNotContain("Contato direto com os anunciantes")
                .doesNotContain("Privacidade e discrição na navegação")
                .doesNotContain("Anúncios com moderação contínua")
                .doesNotContain("Busca simples, rápida e segura");
    }
}
