package br.com.topsdojob.v3.importacao.conteudoseo;

import br.com.topsdojob.v3.application.blog.BlogConteudoValidator;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SanitizadorConteudoLegado {
  private static final Set<String> TAGS_BLOG =
      Set.of("p", "br", "strong", "b", "em", "i", "h2", "h3", "ul", "ol", "li", "a");
  private static final Pattern BLOCO_BLOQUEADO = Pattern.compile(
      "(?is)<\\s*(script|iframe|style|object|embed|form|svg|video|audio|template)"
          + "\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");
  private static final Pattern TAG_BLOQUEADA = Pattern.compile(
      "(?is)<\\s*/?\\s*(script|iframe|style|object|embed|form|input|button|svg|video|audio"
          + "|meta|base|link|template)\\b[^>]*>");
  private static final Pattern COMENTARIO = Pattern.compile("(?s)<!--.*?-->");
  private static final Pattern TAG = Pattern.compile("(?is)<\\s*(/?)\\s*([a-z0-9]+)\\b([^>]*)>");
  private static final Pattern HREF = Pattern.compile(
      "(?is)\\bhref\\s*=\\s*([\"'])(.*?)\\1");
  private static final Pattern PROTOCOLO_PERIGOSO =
      Pattern.compile("(?i)(?:javascript|vbscript|data)\\s*:");
  private static final Pattern LINK_MARKDOWN_PERIGOSO = Pattern.compile(
      "(?i)\\]\\s*\\(\\s*(?:javascript|vbscript|data)\\s*:[^)]*\\)");
  private static final Pattern CONTROLE = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

  private final BlogConteudoValidator blogValidator;

  public SanitizadorConteudoLegado(BlogConteudoValidator blogValidator) {
    this.blogValidator = blogValidator;
  }

  public Resultado sanitizarBlog(String valor) {
    String original = normalizarQuebras(valor);
    String limpo = removerBlocosPerigosos(original);
    Matcher matcher = TAG.matcher(limpo);
    StringBuffer resultado = new StringBuffer();
    while (matcher.find()) {
      String fechamento = matcher.group(1);
      String nome = matcher.group(2).toLowerCase(Locale.ROOT);
      String atributos = matcher.group(3);
      String substituto = reconstruirTag(fechamento, nome, atributos);
      matcher.appendReplacement(resultado, Matcher.quoteReplacement(substituto));
    }
    matcher.appendTail(resultado);
    String seguro = PROTOCOLO_PERIGOSO.matcher(resultado.toString())
        .replaceAll("protocolo-removido:")
        .replaceAll("[ \\t]+\\n", "\n")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
    seguro = blogValidator.validarRascunho(seguro);
    return new Resultado(seguro, !seguro.equals(original));
  }

  public Resultado sanitizarMarkdown(String valor) {
    String original = normalizarQuebras(valor);
    String seguro = removerBlocosPerigosos(original)
        .replaceAll("(?is)<[^>]+>", " ")
        .replaceAll("[ \\t]+", " ")
        .replaceAll(" *\\n *", "\n");
    seguro = LINK_MARKDOWN_PERIGOSO.matcher(seguro).replaceAll("](link-removido)");
    seguro = PROTOCOLO_PERIGOSO.matcher(seguro).replaceAll("protocolo-removido:");
    seguro = CONTROLE.matcher(seguro).replaceAll("").replaceAll("\\n{3,}", "\n\n").trim();
    return new Resultado(seguro, !seguro.equals(original));
  }

  public Resultado sanitizarTexto(String valor) {
    Resultado markdown = sanitizarMarkdown(valor);
    String seguro = markdown.conteudo()
        .replaceAll("[\\r\\n\\t]+", " ")
        .replaceAll("\\s{2,}", " ")
        .trim();
    return new Resultado(seguro, !seguro.equals(normalizarQuebras(valor)));
  }

  private String reconstruirTag(String fechamento, String nome, String atributos) {
    if (!TAGS_BLOG.contains(nome)) {
      return "";
    }
    if (!fechamento.isEmpty()) {
      return "br".equals(nome) ? "" : "</" + nome + ">";
    }
    if ("br".equals(nome)) {
      return "<br>";
    }
    if (!"a".equals(nome)) {
      return "<" + nome + ">";
    }
    Matcher href = HREF.matcher(atributos == null ? "" : atributos);
    if (!href.find()) {
      return "<a>";
    }
    String destino = href.group(2).trim();
    if (!linkSeguro(destino)) {
      return "<a>";
    }
    return "<a href=\"" + escaparAtributo(destino) + "\">";
  }

  private boolean linkSeguro(String valor) {
    return (valor.startsWith("/") && !valor.startsWith("//"))
        || valor.startsWith("https://")
        || valor.startsWith("http://");
  }

  private String removerBlocosPerigosos(String valor) {
    String semComentarios = COMENTARIO.matcher(valor).replaceAll("");
    String semBlocos = BLOCO_BLOQUEADO.matcher(semComentarios).replaceAll("");
    return TAG_BLOQUEADA.matcher(semBlocos).replaceAll("");
  }

  private String escaparAtributo(String valor) {
    return valor.replace("&", "&amp;").replace("\"", "&quot;");
  }

  private String normalizarQuebras(String valor) {
    if (valor == null) {
      return "";
    }
    return CONTROLE.matcher(valor.replace("\r\n", "\n").replace('\r', '\n'))
        .replaceAll("");
  }

  public record Resultado(String conteudo, boolean alterado) {
  }
}
