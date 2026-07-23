package br.com.topsdojob.v3.web.admin.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAnuncioRemocaoContratoTest {

  @Test
  void contratoMantemRemocaoLogicaEDelegaLimpezaFisicaAoStorageCanonico() throws Exception {
    String controller = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3", "web", "admin",
        "anuncio", "AdminAnuncioRemocaoController.java"));
    String service = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3", "application", "admin",
        "anuncio", "AdminAnuncioRemocaoService.java"));
    String cleanup = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3", "application", "admin",
        "anuncio", "AdminAnuncioMidiaCleanupService.java"));
    String repository = Files.readString(Path.of(
        "src", "main", "java", "br", "com", "topsdojob", "v3", "persistence",
        "repository", "AnuncioRepository.java"));
    String openApi = Files.readString(Path.of(
        "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

    assertThat(controller)
        .contains("@PostMapping(\"/{id}/remocao-logica\")")
        .contains("hasRole('ADMIN') and hasAuthority('ANUNCIO_MODERAR')")
        .doesNotContain("@DeleteMapping");
    assertThat(service)
        .contains("findByIdForModeration")
        .contains("removerLogicamente")
        .contains("ANUNCIO_REMOVIDO_ADMINISTRATIVAMENTE")
        .contains("ANUNCIO_MIDIAS_EXCLUIDAS_R2")
        .contains("midiaCleanupService.limpar")
        .doesNotContain(".delete(");
    assertThat(cleanup)
        .contains("ObjectStorage")
        .contains("storage.delete")
        .contains("storage.exists")
        .contains("StorageArea.PUBLIC_MEDIA")
        .contains("StorageArea.PRIVATE_MEDIA")
        .contains("ARQUIVO_DOCUMENTAL_VINCULADO")
        .contains("objetosCompartilhadosPreservados");
    assertThat(repository)
        .contains("@Lock(LockModeType.PESSIMISTIC_WRITE)")
        .contains("findByIdForModeration");
    assertThat(openApi)
        .contains("/api/admin/anuncios/{id}/remocao-logica:")
        .contains("postAdminAnuncioRemocaoLogica")
        .contains("AdminAnuncioRemocaoRequest")
        .contains("AdminAnuncioRemocao")
        .contains("enum: [REMOVIDO]")
        .contains("objetosR2Excluidos")
        .contains("ServiceUnavailable");
  }
}
