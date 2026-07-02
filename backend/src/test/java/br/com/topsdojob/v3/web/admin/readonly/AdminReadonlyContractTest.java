package br.com.topsdojob.v3.web.admin.readonly;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminReadonlyContractTest {

    @Test
    void controllersAdminReadonlyUsamSomenteGetComPreAuthorize() throws Exception {
        String controllers = Files.walk(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "web",
                "admin",
                "readonly"))
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(controllers)
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@PatchMapping")
                .doesNotContain("@DeleteMapping");
        assertThat(controllers).contains("@PreAuthorize");
        assertThat(controllers)
                .doesNotContain("aprovar")
                .doesNotContain("reprovar")
                .doesNotContain("excluir")
                .doesNotContain("pix")
                .doesNotContain("pagamento");
    }

    @Test
    void dtosReadonlyNaoExpoemCamposSensiveis() throws Exception {
        String dtos = Files.walk(Path.of(
                "src",
                "main",
                "java",
                "br",
                "com",
                "topsdojob",
                "v3",
                "application",
                "admin",
                "readonly",
                "dto"))
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(dtos)
                .doesNotContain("DocumentoUsuario")
                .doesNotContain("cpf")
                .doesNotContain("whatsappNormalizado")
                .doesNotContain("telefone")
                .doesNotContain("storageProvider")
                .doesNotContain("chaveObjeto")
                .doesNotContain("bucket")
                .doesNotContain("sha256")
                .doesNotContain("senha")
                .doesNotContain("token")
                .doesNotContain("payloadSolicitado");
    }
}
