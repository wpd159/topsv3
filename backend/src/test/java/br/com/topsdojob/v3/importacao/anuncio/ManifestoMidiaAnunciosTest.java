package br.com.topsdojob.v3.importacao.anuncio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestoMidiaAnunciosTest {

  @Test
  void exigeOriginalCapaValidaEModeracaoAprovadaParaMidiaPublica() {
    var valida = item(true, true, true, "APROVADA", "CAPA", "FOTO", "LIVRE");
    var semOriginal = item(false, true, true, "APROVADA", "CAPA", "FOTO", "LIVRE");
    var capaInvalida = item(true, true, false, "APROVADA", "CAPA", "FOTO", "LIVRE");

    var manifesto = new ManifestoMidiaAnuncios(List.of(valida, semOriginal, capaInvalida));

    assertThat(valida.publicaValidaComprovada()).isTrue();
    assertThat(semOriginal.publicaValidaComprovada()).isFalse();
    assertThat(capaInvalida.publicaValidaComprovada()).isFalse();
    assertThat(manifesto.itensPublicosValidos("anuncio-sintetico")).containsExactly(valida);
  }

  @Test
  void videoPersistivelContinuaRestritoAClassificacaoDezoitoMais() {
    var videoLivre = item(true, true, false, "APROVADA", "GALERIA", "VIDEO", "LIVRE");
    var videoRestrito = item(
        true, true, false, "APROVADA", "GALERIA", "VIDEO", "RESTRITA_18");

    assertThat(videoLivre.persistivel()).isTrue();
    assertThat(videoLivre.publicaValidaComprovada()).isFalse();
    assertThat(videoRestrito.publicaValidaComprovada()).isTrue();
  }

  private ManifestoMidiaAnuncios.Item item(
      boolean originalExiste,
      boolean publicaValida,
      boolean capaValida,
      String statusModeracao,
      String finalidade,
      String tipo,
      String visibilidade) {
    return new ManifestoMidiaAnuncios.Item(
        "midia-sintetica-" + finalidade + "-" + tipo + "-" + visibilidade,
        "anuncio-sintetico",
        "referencia-sintetica-" + finalidade + "-" + tipo + "-" + visibilidade,
        originalExiste,
        publicaValida,
        capaValida,
        false,
        statusModeracao,
        "ANUNCIO",
        finalidade,
        tipo,
        "bucket-sintetico",
        "chave-sintetica",
        "VIDEO".equals(tipo) ? "video/mp4" : "image/jpeg",
        1024L,
        0,
        visibilidade);
  }
}
