package br.com.topsdojob.v3.application.faq;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class FaqDtos {

  private FaqDtos() {
  }

  public record Item(
      UUID id,
      String pergunta,
      String resposta,
      String categoria,
      String categoriaRotulo,
      String status,
      int ordem,
      OffsetDateTime publicadoEm,
      OffsetDateTime atualizadoEm,
      long versao) {
  }

  public record Edicao(
      String pergunta,
      String resposta,
      String categoria,
      Integer ordem,
      Long versao) {
  }

  public record Versao(Long versao) {
  }

  public record Ordem(Integer ordem, Long versao) {
  }
}
