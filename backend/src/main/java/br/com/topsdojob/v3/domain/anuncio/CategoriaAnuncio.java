package br.com.topsdojob.v3.domain.anuncio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum CategoriaAnuncio {
  ACOMPANHANTE_FEMININA("Acompanhante feminina"),
  ACOMPANHANTE_MASCULINO("Acompanhante masculino"),
  TRANSEX_TRAVESTIS("Trans / Travestis"),
  MASSAGENS("Massagens"),
  VENDA_DE_CONTEUDO("Sexo Virtual");

  private final String nomePublico;

  CategoriaAnuncio(String nomePublico) {
    this.nomePublico = nomePublico;
  }

  public String nomePublico() {
    return nomePublico;
  }

  public String destinoPublico() {
    return "/anuncios?categoria=" + name();
  }

  public static Optional<CategoriaAnuncio> porCodigo(String codigo) {
    if (codigo == null || codigo.isBlank()) {
      return Optional.empty();
    }
    String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(categoria -> categoria.name().equals(normalizado))
        .findFirst();
  }
}
