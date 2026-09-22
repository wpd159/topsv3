package br.com.topsdojob.v3.application.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BlogConteudoValidatorTest {

  private final BlogConteudoValidator validator = new BlogConteudoValidator();

  @Test
  void preservaFormatacaoELinksSeguros() {
    String content = """
        <h2>Guia editorial seguro</h2>

        <p>Conteudo com <strong>enfase</strong> e <a href="/anuncios">link interno</a>.</p>

        <ul><li>Primeiro item</li><li>Segundo item</li></ul>
        """;

    assertThat(validator.validar(content)).contains("<h2>", "<strong>", "href=\"/anuncios\"");
  }

  @Test
  void preservaIntegralmenteCorpoLongoNoRascunhoENaPublicacao() {
    String content = "<h2>Inicio do artigo sintetico</h2>\n"
        + "<p>Texto editorial com <strong>enfase</strong> e acentuação preservada.</p>\n".repeat(1000)
        + "<p>Marcador final do artigo sintetico.</p>";

    assertThat(validator.validarRascunho(content)).isEqualTo(content);
    assertThat(validator.validar(content)).isEqualTo(content);
  }

  @Test
  void rejeitaScriptHandlersProtocolosETextoOculto() {
    assertThatThrownBy(() -> validator.validar("<p onclick=\"x()\">conteudo editorial suficientemente longo</p>"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> validator.validar("<script>alert(1)</script> conteudo editorial suficientemente longo"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> validator.validar("<a href=\"javascript:x\">conteudo editorial suficientemente longo</a>"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> validator.validar("<p style=\"display:none\">conteudo editorial suficientemente longo</p>"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void permiteRascunhoVazioSemAfrouxarPublicacao() {
    assertThat(validator.validarRascunho("")).isEmpty();
    assertThatThrownBy(() -> validator.validar(""))
        .isInstanceOf(ResponseStatusException.class);
  }
}
