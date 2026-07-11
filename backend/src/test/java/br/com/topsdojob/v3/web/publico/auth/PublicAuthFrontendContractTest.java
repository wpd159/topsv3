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
                .contains("/usuarios/verificar-duplicidade")
                .contains("credentials: 'include'")
                .contains("csrfHeaderName()")
                .contains("['X', 'XSRF', 'TOKEN'].join('-')")
                .doesNotContain("/api/admin/auth");
        assertThat(FRONTEND.resolve(Path.of("features", "auth", "register", "register-api.ts")))
                .doesNotExist();
    }

    @Test
    void loginCadastroESessaoDelegamAoAdapterSemFetchConcorrente() throws Exception {
        String loginModal = Files.readString(FRONTEND.resolve(Path.of("components", "modals", "login-modal.tsx")));
        String registerForm = Files.readString(FRONTEND.resolve(Path.of("components", "auth", "register-form.tsx")));
        String authContext = Files.readString(FRONTEND.resolve(Path.of("context", "AuthContext.tsx")));

        assertThat(loginModal).contains("loginPublic").doesNotContain("fetch(`${API}/auth/login`");
        assertThat(registerForm).contains("@/lib/public-auth-api").doesNotContain("features/auth/register/register-api");
        assertThat(authContext).contains("getPublicSession").contains("logoutPublic");
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
