package br.com.topsdojob.v3.application.metrica;

import java.util.Objects;

public record VisualizacoesCanonicasDto(
    Long total,
    Situacao situacao) {

  public VisualizacoesCanonicasDto {
    Objects.requireNonNull(situacao, "situacao obrigatoria");
    if (situacao == Situacao.HISTORICO_PENDENTE) {
      if (total != null) {
        throw new IllegalArgumentException("historico pendente nao pode expor total parcial");
      }
    } else if (total == null || total < 0) {
      throw new IllegalArgumentException("total canonico deve ser nao negativo");
    } else if (situacao == Situacao.ZERO_LEGITIMO && total != 0) {
      throw new IllegalArgumentException("zero legitimo exige total igual a zero");
    } else if (situacao == Situacao.DISPONIVEL && total == 0) {
      throw new IllegalArgumentException("total disponivel deve ser positivo");
    }
  }

  public static VisualizacoesCanonicasDto total(long total) {
    return new VisualizacoesCanonicasDto(
        total,
        total == 0 ? Situacao.ZERO_LEGITIMO : Situacao.DISPONIVEL);
  }

  public static VisualizacoesCanonicasDto historicoPendente() {
    return new VisualizacoesCanonicasDto(null, Situacao.HISTORICO_PENDENTE);
  }

  public enum Situacao {
    DISPONIVEL,
    ZERO_LEGITIMO,
    HISTORICO_PENDENTE
  }
}
