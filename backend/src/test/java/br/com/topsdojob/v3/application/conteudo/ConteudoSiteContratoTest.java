package br.com.topsdojob.v3.application.conteudo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConteudoSiteContratoTest {

  @Test
  void openApiDescreveFontePublicaEAdministrativaUnicas() throws Exception {
    String openApi = Files.readString(Path.of(
        "..", "contracts", "openapi", "topsdojob-v3-local.yaml"));

    assertThat(openApi)
        .contains("/api/public/conteudos-site:")
        .contains("/api/admin/conteudos-site:")
        .contains("/api/admin/conteudos-site/{contentKey}:")
        .contains("putAdminConteudoSite")
        .contains("Markdown seguro")
        .doesNotContain("/api/public/api/public/conteudos-site")
        .doesNotContain("/api/public/api/admin/conteudos-site");
  }
}
