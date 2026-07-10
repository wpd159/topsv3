package br.com.topsdojob.v3.web.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminFrontendStorageTest {

    @Test
    void frontendAdminNaoUsaLocalStorageOuSessionStorage() throws Exception {
        Path frontendAdmin = Path.of("..", "frontend", "src");
        String text = Files.walk(frontendAdmin)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().contains("admin") || path.toString().contains("api"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(text).doesNotContain("localStorage").doesNotContain("sessionStorage");
    }

    @Test
    void frontendAdminUsaCookieDeSessaoSemCredencialHardcoded() throws Exception {
        String api = Files.readString(Path.of("..", "frontend", "src", "context", "AuthContext.tsx"));
        String readonlyApi = Files.readString(Path.of(
                "..", "frontend", "src", "features", "moderation-v2", "api", "client.ts"));
        String panel = Files.readString(Path.of(
                "..",
                "frontend",
                "src",
                "components",
                "modals",
                "login-modal.tsx"));

        assertThat(api).containsPattern("credentials:\\s*['\"]include['\"]");
        assertThat(readonlyApi).containsPattern("credentials:\\s*['\"]include['\"]");
        assertThat(api).doesNotContain("Bearer").doesNotContain("Authorization");
        assertThat(readonlyApi).doesNotContain("Bearer").doesNotContain("Authorization");
        assertThat(panel).doesNotContain("SenhaSintetica").doesNotContain("NaoUsar123");
        assertThat(panel).doesNotContain("admin.local@example.invalid");
    }
}
