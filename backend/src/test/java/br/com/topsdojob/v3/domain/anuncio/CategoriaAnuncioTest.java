package br.com.topsdojob.v3.domain.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CategoriaAnuncioTest {

  @Test
  void preservaExatamenteATaxonomiaDoWizard() {
    assertThat(Arrays.stream(CategoriaAnuncio.values()).map(Enum::name))
        .containsExactly(
            "ACOMPANHANTE_FEMININA",
            "ACOMPANHANTE_MASCULINO",
            "TRANSEX_TRAVESTIS",
            "MASSAGENS",
            "VENDA_DE_CONTEUDO");
  }

  @Test
  void derivaDestinoPublicoSemCodigoLivre() {
    assertThat(CategoriaAnuncio.MASSAGENS.destinoPublico())
        .isEqualTo("/anuncios?categoria=MASSAGENS");
    assertThat(CategoriaAnuncio.porCodigo(" encontros_casuais ")).isEmpty();
  }
}
