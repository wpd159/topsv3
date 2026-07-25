package br.com.topsdojob.v3.application.conteudo;

import java.util.List;
import java.util.Optional;

public final class ConteudoSiteCatalogo {

  private static final List<Definicao> DEFINICOES = List.of(
      new Definicao("quem-somos", "quem_somos", "/sobre"),
      new Definicao("footer-resumo-institucional", "footer_resumo_institucional", "/sobre"),
      new Definicao("termos-de-uso", "termos_de_uso", "/termos-de-uso"),
      new Definicao("politica-privacidade", "politica_privacidade", "/politica-de-privacidade"),
      new Definicao("politica-cookies", "politica_cookies", "/cookies"),
      new Definicao("consentimento-promocional", "consentimento_promocional", "/consentimento-promocional"),
      new Definicao("verificacao", "verificacao", "/politicas/verificacao-etaria"),
      new Definicao("popup-login", "popup_login", "/termos-de-uso"),
      new Definicao("texto-whatsapp", "texto_whatsapp", "/aviso-seguranca-whatsapp"),
      new Definicao(
          "termos-conteudo-restrito",
          "termos_conteudo_restrito",
          "/politicas/termos-conteudo-restrito"),
      new Definicao(
          "privacidade-conteudo-restrito",
          "privacidade_conteudo_restrito",
          "/politicas/privacidade-conteudo-restrito"),
      new Definicao(
          "aviso-legal-conteudo-restrito",
          "aviso_legal_conteudo_restrito",
          "/politicas/aviso-legal-conteudo-restrito"));

  private ConteudoSiteCatalogo() {
  }

  public static List<Definicao> todas() {
    return DEFINICOES;
  }

  public static Optional<Definicao> porChavePublica(String chavePublica) {
    return DEFINICOES.stream()
        .filter(definicao -> definicao.chavePublica().equals(chavePublica))
        .findFirst();
  }

  public record Definicao(String chavePublica, String chavePersistida, String caminhoPublico) {
  }
}
