package br.com.topsdojob.v3.importacao.anuncio;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ManifestoMidiaAnuncios(List<Item> itens) {

  public ManifestoMidiaAnuncios {
    itens = List.copyOf(itens == null ? List.of() : itens);
  }

  public List<Item> itensDoAnuncio(String anuncioOrigemId) {
    return itens.stream()
        .filter(item -> item.anuncioOrigemId().equals(anuncioOrigemId))
        .toList();
  }

  public List<Item> itensPublicosValidos(String anuncioOrigemId) {
    return itensDoAnuncio(anuncioOrigemId).stream()
        .filter(Item::publicaValidaComprovada)
        .toList();
  }

  public Optional<Item> porReferencia(String referenciaOrigemId) {
    return itens.stream()
        .filter(item -> item.referenciaOrigemId().equals(referenciaOrigemId))
        .findFirst();
  }

  public record Item(
      String idOrigem,
      String anuncioOrigemId,
      String referenciaOrigemId,
      boolean originalExiste,
      boolean publicaValida,
      boolean capaValida,
      boolean quarentena,
      String statusModeracao,
      String ownership,
      String finalidade,
      String tipo,
      String bucketDestino,
      String chaveDestino,
      String mimeType,
      long tamanhoBytes,
      int ordem,
      String visibilidade) {

    public Item {
      idOrigem = exigir(idOrigem, "idOrigem");
      anuncioOrigemId = exigir(anuncioOrigemId, "anuncioOrigemId");
      referenciaOrigemId = exigir(referenciaOrigemId, "referenciaOrigemId");
      statusModeracao = normalizar(statusModeracao);
      ownership = normalizar(ownership);
      finalidade = normalizar(finalidade);
      tipo = normalizar(tipo);
      bucketDestino = opcional(bucketDestino);
      chaveDestino = opcional(chaveDestino);
      mimeType = opcional(mimeType);
      visibilidade = normalizar(visibilidade);
    }

    public boolean publicaValidaComprovada() {
      return persistivel()
          && publicaValida
          && "APROVADA".equals(statusModeracao)
          && ("CAPA".equals(finalidade) || "GALERIA".equals(finalidade))
          && (!"CAPA".equals(finalidade) || capaValida)
          && ("LIVRE".equals(visibilidade) || "RESTRITA_18".equals(visibilidade))
          && (!"VIDEO".equals(tipo) || "RESTRITA_18".equals(visibilidade));
    }

    public boolean persistivel() {
      return originalExiste
          && !quarentena
          && "ANUNCIO".equals(ownership)
          && ("FOTO".equals(tipo) || "VIDEO".equals(tipo))
          && bucketDestino != null
          && chaveDestino != null
          && mimeType != null
          && tamanhoBytes > 0;
    }

    private static String exigir(String valor, String campo) {
      String normalizado = opcional(valor);
      if (normalizado == null) {
        throw new IllegalArgumentException(campo + " deve ser informado");
      }
      return normalizado;
    }

    private static String normalizar(String valor) {
      return exigir(valor, "valor").toUpperCase(Locale.ROOT);
    }

    private static String opcional(String valor) {
      return valor == null || valor.isBlank() ? null : valor.trim();
    }
  }
}
