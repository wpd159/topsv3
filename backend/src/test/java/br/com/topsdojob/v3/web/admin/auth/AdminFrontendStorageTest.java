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
        String api = Files.readString(Path.of("..", "frontend", "src", "lib", "api", "adminAuthApi.ts"));
        String readonlyApi = Files.readString(Path.of("..", "frontend", "src", "lib", "api", "adminReadonlyApi.ts"));
        String panel = Files.readString(Path.of(
                "..",
                "frontend",
                "src",
                "modules",
                "admin",
                "shell",
                "AdminAuthPanel.tsx"));

        assertThat(api).contains("credentials: \"include\"");
        assertThat(readonlyApi).contains("credentials: \"include\"");
        assertThat(api).doesNotContain("Bearer").doesNotContain("Authorization");
        assertThat(readonlyApi).doesNotContain("Bearer").doesNotContain("Authorization");
        assertThat(panel).doesNotContain("SenhaSintetica").doesNotContain("NaoUsar123");
        assertThat(panel).doesNotContain("useState(\"admin.local@example.invalid\")");
        assertThat(panel).doesNotContain("placeholder=\"admin.local@example.invalid\"");
        assertThat(panel).contains("placeholder=\"admin@example.invalid\"");
    }
}
