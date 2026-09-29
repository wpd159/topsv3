package br.com.topsdojob.v3.web.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminFrontendStorageTest {

    @Test
    void frontendAdminNaoUsaLocalStorageESomentePersisteReferenciaDeAprovacaoNoSessionStorage() throws Exception {
        Path frontendAdmin = Path.of("..", "frontend", "src");
        assertThat(Files.isRegularFile(frontendAdmin.resolve("features/admin-anuncios/admin-anuncio-moderacao.tsx")))
                .isTrue();
        assertThat(Files.isRegularFile(frontendAdmin.resolve("lib/admin-auth-api.ts"))).isTrue();

        try (var paths = Files.walk(frontendAdmin)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().contains("admin") || file.toString().contains("api"))
                    .toList()) {
                String relativePath = frontendAdmin.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path);
                assertThat(source).as(relativePath).doesNotContain("localStorage");

                List<String> sessionStorageUses = source.lines()
                        .map(String::trim)
                        .filter(line -> line.contains("sessionStorage"))
                        .toList();
                switch (relativePath) {
                    case "features/admin-anuncios/admin-anuncio-moderacao.tsx" ->
                            assertThat(sessionStorageUses).as(relativePath).containsExactly(
                                    "window.sessionStorage.setItem(approvalStorageKey(actorId, operation.anuncioId), JSON.stringify(operation))",
                                    "try { window.sessionStorage.removeItem(approvalStorageKey(actorId, anuncioId)) } catch { /* Não altera o resultado confirmado. */ }",
                                    "const raw = window.sessionStorage.getItem(approvalStorageKey(actorId, anuncioId))");
                    case "lib/admin-auth-api.ts" ->
                            assertThat(sessionStorageUses).as(relativePath).containsExactly(
                                    "for (let index = window.sessionStorage.length - 1; index >= 0; index -= 1) {",
                                    "const key = window.sessionStorage.key(index)",
                                    "if (key?.startsWith('tops-admin-approval-v1:')) window.sessionStorage.removeItem(key)");
                    default -> assertThat(sessionStorageUses).as(relativePath).isEmpty();
                }
            }
        }
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
