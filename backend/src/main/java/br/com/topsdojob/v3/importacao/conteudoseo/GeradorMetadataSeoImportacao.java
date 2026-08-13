package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LinkInterno;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.LocalidadeSeoLegada;
import br.com.topsdojob.v3.importacao.conteudoseo.SnapshotConteudoSeoFaseDois.TipoLocalidade;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GeradorMetadataSeoImportacao {
  static final String DOMINIO_CANONICO = "https://topsdojob.com";

  public MetadataSeo gerarLocalidade(LocalidadeSeoLegada localidade) {
    String uf = localidade.uf().toUpperCase(Locale.ROOT);
    String h1 = switch (localidade.tipo()) {
      case ESTADO -> "Acompanhantes em " + localidade.nomeLocalidade();
      case CIDADE -> "Acompanhantes em " + localidade.nomeLocalidade() + " - " + uf;
      case BAIRRO -> "Acompanhantes em " + localidade.nomeLocalidade() + ", "
          + localidade.nomeCidade() + " - " + uf;
    };
    String titulo = limitar(h1 + " - Tops do Job", 70);
    String contexto = switch (localidade.tipo()) {
      case ESTADO -> localidade.nomeLocalidade();
      case CIDADE -> localidade.nomeLocalidade() + " - " + uf;
      case BAIRRO -> localidade.nomeLocalidade() + ", "
          + localidade.nomeCidade() + " - " + uf;
    };
    String descricao = localidade.anunciosPublicosIndexaveis() > 0
        ? "Consulte " + localidade.anunciosPublicosIndexaveis()
            + " anuncios publicos indexaveis em " + contexto
            + " e acesse localidades relacionadas no Tops do Job."
        : "Consulte informacoes da localidade " + contexto
            + ". Esta pagina ainda nao possui inventario publico indexavel.";
    String introducao = localidade.introducao();
    if (introducao == null || introducao.isBlank()) {
      introducao = localidade.anunciosPublicosIndexaveis() > 0
          ? "Encontre anuncios publicos em " + contexto
              + " e navegue pelas localidades realmente disponiveis."
          : "A localidade " + contexto
              + " permanece acessivel, mas ainda nao atende aos criterios de indexacao.";
    }
    List<LinkInterno> links = localidade.linksInternos().stream()
        .filter(link -> caminhoPublicoSeguro(link.caminho()))
        .distinct()
        .toList();
    return new MetadataSeo(
        titulo,
        limitar(descricao, 160),
        h1,
        introducao,
        links,
        dadosEstruturados(localidade, h1, links));
  }

  public MetadataSeo gerarEditorial(
      String tituloPublico,
      String descricaoPublica,
      String caminhoCanonico) {
    String titulo = limitar(tituloPublico + " - Tops do Job", 70);
    String descricao = limitar(descricaoPublica, 160);
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("@context", "https://schema.org");
    schema.put("@type", "WebPage");
    schema.put("name", tituloPublico);
    schema.put("url", DOMINIO_CANONICO + caminhoCanonico);
    return new MetadataSeo(
        titulo,
        descricao,
        tituloPublico,
        descricaoPublica,
        List.of(),
        schema);
  }

  public boolean caminhoPublicoSeguro(String caminho) {
    return caminho != null
        && caminho.startsWith("/")
        && !caminho.startsWith("//")
        && !caminho.contains("?")
        && !caminho.contains("#")
        && !caminho.contains("..")
        && !caminho.toLowerCase(Locale.ROOT).contains("v3.esle.cloud")
        && caminho.matches("^/[a-z0-9/_-]*$");
  }

  private Map<String, Object> dadosEstruturados(
      LocalidadeSeoLegada localidade,
      String h1,
      List<LinkInterno> links) {
    List<Map<String, Object>> grafo = new ArrayList<>();
    grafo.add(breadcrumb(localidade));
    if (!links.isEmpty()) {
      List<Map<String, Object>> elementos = new ArrayList<>();
      for (int indice = 0; indice < links.size(); indice++) {
        LinkInterno link = links.get(indice);
        elementos.add(Map.of(
            "@type", "ListItem",
            "position", indice + 1,
            "name", link.rotulo(),
            "url", DOMINIO_CANONICO + link.caminho()));
      }
      grafo.add(Map.of(
          "@type", "ItemList",
          "name", h1,
          "numberOfItems", elementos.size(),
          "itemListElement", elementos));
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("@context", "https://schema.org");
    schema.put("@graph", grafo);
    return schema;
  }

  private Map<String, Object> breadcrumb(LocalidadeSeoLegada localidade) {
    String uf = localidade.uf().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> elementos = new ArrayList<>();
    adicionarBreadcrumb(elementos, "Inicio", "/");
    adicionarBreadcrumb(elementos, "Acompanhantes", "/acompanhantes");
    if (localidade.tipo() != TipoLocalidade.ESTADO) {
      adicionarBreadcrumb(elementos, localidade.uf().toUpperCase(Locale.ROOT),
          "/acompanhantes/" + uf);
    }
    if (localidade.tipo() == TipoLocalidade.BAIRRO) {
      String[] partes = localidade.caminhoPublico().split("/");
      String cidadeSlug = partes.length > 3 ? partes[3] : "";
      adicionarBreadcrumb(
          elementos,
          localidade.nomeCidade(),
          "/acompanhantes/" + uf + "/" + cidadeSlug);
    }
    adicionarBreadcrumb(elementos, localidade.nomeLocalidade(), localidade.caminhoPublico());
    return Map.of("@type", "BreadcrumbList", "itemListElement", elementos);
  }

  private void adicionarBreadcrumb(
      List<Map<String, Object>> elementos,
      String nome,
      String caminho) {
    elementos.add(Map.of(
        "@type", "ListItem",
        "position", elementos.size() + 1,
        "name", nome,
        "item", DOMINIO_CANONICO + caminho));
  }

  private String limitar(String valor, int limite) {
    String normalizado = valor == null ? "" : valor.replaceAll("\\s+", " ").trim();
    if (normalizado.length() <= limite) {
      return normalizado;
    }
    int corte = normalizado.lastIndexOf(' ', limite - 1);
    return normalizado.substring(0, corte > 20 ? corte : limite).trim();
  }

  public record MetadataSeo(
      String titulo,
      String descricao,
      String h1,
      String introducao,
      List<LinkInterno> linksInternos,
      Map<String, Object> schemaJson) {
  }
}
