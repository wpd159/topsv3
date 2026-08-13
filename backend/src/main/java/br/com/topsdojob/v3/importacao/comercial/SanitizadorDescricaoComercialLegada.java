package br.com.topsdojob.v3.importacao.comercial;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class SanitizadorDescricaoComercialLegada {
  private static final Pattern BLOCOS = Pattern.compile(
      "(?is)<\\s*(script|style|iframe|object|embed|form|svg)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");
  private static final Pattern TAGS = Pattern.compile("(?is)<[^>]+>");
  private static final Pattern CONTROLES = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

  public String sanitizar(String valor, int limite) {
    String seguro = valor == null ? "" : valor;
    seguro = BLOCOS.matcher(seguro).replaceAll(" ");
    seguro = TAGS.matcher(seguro).replaceAll(" ");
    seguro = CONTROLES.matcher(seguro).replaceAll("");
    seguro = seguro.replaceAll("[\\r\\n\\t]+", " ")
        .replaceAll("\\s{2,}", " ")
        .trim();
    return seguro.length() <= limite ? seguro : seguro.substring(0, limite).trim();
  }
}
