package br.com.topsdojob.v3.application.blog;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BlogConteudoValidator {

  private static final Pattern BLOQUEADO = Pattern.compile(
      "(?is)<\\s*(script|iframe|style|object|embed|form|input|button|svg|video|audio|meta|base|link)\\b"
          + "|\\bon[a-z][a-z0-9-]*\\s*="
          + "|(?:javascript|vbscript|data)\\s*:"
          + "|<!--"
          + "|\\b(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden|opacity\\s*:\\s*0)\\b");
  private static final Pattern TAG = Pattern.compile("(?is)</?\\s*([a-z0-9]+)\\b[^>]*>");
  private static final Pattern LINK = Pattern.compile(
      "(?is)<a\\s+[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>");
  private static final Set<String> TAGS = Set.of(
      "p", "br", "strong", "b", "em", "i", "h2", "h3", "ul", "ol", "li", "a");

  public String validar(String valor) {
    String conteudo = valor == null ? "" : valor.replace("\r\n", "\n").trim();
    if (conteudo.length() < 40 || conteudo.length() > 200_000) {
      throw badRequest("conteudo deve ter entre 40 e 200000 caracteres");
    }
    if (BLOQUEADO.matcher(conteudo).find()) {
      throw badRequest("conteudo editorial contem HTML ou protocolo nao permitido");
    }
    var tags = TAG.matcher(conteudo);
    while (tags.find()) {
      if (!TAGS.contains(tags.group(1).toLowerCase())) {
        throw badRequest("conteudo editorial contem tag nao permitida");
      }
    }
    var links = LINK.matcher(conteudo);
    while (links.find()) {
      String href = links.group(2).trim();
      if (!(href.startsWith("/")
          || href.startsWith("https://")
          || href.startsWith("http://"))) {
        throw badRequest("conteudo editorial contem link nao permitido");
      }
    }
    return conteudo;
  }

  public String validarRascunho(String valor) {
    String conteudo = valor == null ? "" : valor.replace("\r\n", "\n").trim();
    if (conteudo.length() > 200_000) {
      throw badRequest("conteudo deve ter no maximo 200000 caracteres");
    }
    if (conteudo.isEmpty()) {
      return conteudo;
    }
    if (BLOQUEADO.matcher(conteudo).find()) {
      throw badRequest("conteudo editorial contem HTML ou protocolo nao permitido");
    }
    var tags = TAG.matcher(conteudo);
    while (tags.find()) {
      if (!TAGS.contains(tags.group(1).toLowerCase())) {
        throw badRequest("conteudo editorial contem tag nao permitida");
      }
    }
    var links = LINK.matcher(conteudo);
    while (links.find()) {
      String href = links.group(2).trim();
      if (!(href.startsWith("/")
          || href.startsWith("https://")
          || href.startsWith("http://"))) {
        throw badRequest("conteudo editorial contem link nao permitido");
      }
    }
    return conteudo;
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
