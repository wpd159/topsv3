package br.com.topsdojob.v3.importacao.conteudoseo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LinkInterno;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.RedirectLegado;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.TipoLocalidade;
import br.com.topsdojob.v3.importacao.model.CodigoPendenciaImportacao;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConteudoSeoFaseDoisPolicyTest {
  private static final OffsetDateTime AGORA =
      OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  private final SanitizadorConteudoLegado sanitizador =
      new SanitizadorConteudoLegado(new BlogConteudoValidator());
  private final PoliticaIndexacaoLocalidadeImportacao politica =
      new PoliticaIndexacaoLocalidadeImportacao();
  private final GeradorMetadataSeoImportacao metadata =
      new GeradorMetadataSeoImportacao();
  private final PlanejadorConteudoSeoImportacao planejador =
      new PlanejadorConteudoSeoImportacao(metadata);

  @Test
  void sanitizaHtmlPerigosoSemDeixarScriptExecutavel() {
    String fonte = """
        <h2 onclick="alert(1)">Guia sintetico</h2>
        <script>alert('nao executar')</script>
        <p>Conteudo editorial util para uma fixture pequena e segura.</p>
        <a href="javascript:alert(2)" style="display:none">link</a>
        <iframe src="https://privado.invalid"></iframe>
        """;

    var resultado = sanitizador.sanitizarBlog(fonte);

    assertThat(resultado.alterado()).isTrue();
    assertThat(resultado.conteudo())
        .contains("<h2>Guia sintetico</h2>")
        .contains("Conteudo editorial util")
        .doesNotContainIgnoringCase(
            "<script", "alert(", "onclick", "javascript:", "<iframe", "display:none");
    new BlogConteudoValidator().validar(resultado.conteudo());
  }

  @Test
  void centralizaIndexacaoSemInventarQuantidadeMinima() {
    LocalidadeSeoLegada indexavel = localidade(
        "cidade-a", "Cidade Alfa", "/acompanhantes/qq/cidade-alfa", 6, true, false);
    LocalidadeSeoLegada vazia = localidade(
        "cidade-b", "Cidade Beta", "/acompanhantes/qq/cidade-beta", 0, false, true);
    LocalidadeSeoLegada somenteAnunciosNoindex = localidade(
        "cidade-c", "Cidade Gama", "/acompanhantes/qq/cidade-gama", 0, true, false);

    assertThat(politica.avaliar(indexavel).indexavel()).isTrue();
    assertThat(politica.avaliar(indexavel).incluirSitemap()).isTrue();
    assertThat(politica.avaliar(vazia).indexavel()).isFalse();
    assertThat(politica.avaliar(vazia).incluirSitemap()).isFalse();
    assertThat(politica.avaliar(somenteAnunciosNoindex).motivo())
        .isEqualTo(PoliticaIndexacaoLocalidadeImportacao.Motivo.SEM_INVENTARIO_INDEXAVEL);
  }

  @Test
  void geraMetadataUnicaEJsonLdSomenteComRelacoesReais() {
    var cidadeAlfa = metadata.gerarLocalidade(localidade(
        "cidade-a", "Cidade Alfa", "/acompanhantes/qq/cidade-alfa", 6, true, false));
    var cidadeBeta = metadata.gerarLocalidade(localidade(
        "cidade-b", "Cidade Beta", "/acompanhantes/qq/cidade-beta", 4, true, false));

    assertThat(cidadeAlfa.titulo()).contains("Cidade Alfa", "QQ");
    assertThat(cidadeBeta.titulo()).contains("Cidade Beta", "QQ");
    assertThat(cidadeAlfa.titulo()).isNotEqualTo(cidadeBeta.titulo());
    assertThat(cidadeAlfa.descricao()).isNotEqualTo(cidadeBeta.descricao());
    assertThat(cidadeAlfa.schemaJson().toString())
        .contains("BreadcrumbList", "ItemList", "topsdojob.com")
        .doesNotContain(
            "AggregateRating", "ratingValue", "price", "availability",
            "v3.esle.cloud", "usuarioId", "objectKey");
  }

  @Test
  void trataSlugEFlattenRedirectDeFormaDeterministica() {
    var slugA = planejador.planejarSlug(
        "Guia Repetido", "post-2", false, valor -> true);
    var slugB = planejador.planejarSlug(
        "Guia Repetido", "post-2", false, valor -> true);
    List<RedirectLegado> redirects = List.of(
        redirect("r1", "/blog/guia-antigo", "/blog/guia-intermediario"),
        redirect("r2", "/blog/guia-intermediario", "/blog/guia-final"));

    assertThat(slugA.slug()).isEqualTo(slugB.slug()).startsWith("guia-repetido-");
    assertThat(planejador.planejarRedirect(redirects.get(0), redirects).destinoFinal())
        .isEqualTo("/blog/guia-final");
    assertThat(planejador.planejarRedirect(redirects.get(0), redirects).saltosEliminados())
        .isEqualTo(1);

    List<RedirectLegado> ciclo = List.of(
        redirect("c1", "/blog/a", "/blog/b"),
        redirect("c2", "/blog/b", "/blog/a"));
    assertThat(planejador.planejarRedirect(ciclo.get(0), ciclo).erro())
        .isEqualTo(CodigoPendenciaImportacao.REDIRECT_CICLICO);
  }

  private static LocalidadeSeoLegada localidade(
      String id,
      String nome,
      String caminho,
      long anunciosIndexaveis,
      boolean inventarioSuficiente,
      boolean vazia) {
    return new LocalidadeSeoLegada(
        id,
        TipoLocalidade.CIDADE,
        caminho,
        nome,
        nome,
        "QQ",
        true,
        true,
        true,
        inventarioSuficiente,
        false,
        false,
        vazia,
        false,
        anunciosIndexaveis,
        "Introducao sintetica e util sobre " + nome + ", baseada em dados agregados reais.",
        List.of(new LinkInterno("/acompanhantes/qq", "Estado QA")),
        AGORA);
  }

  private static RedirectLegado redirect(String id, String origem, String destino) {
    return new RedirectLegado(id, origem, destino, true, null, AGORA);
  }
}
