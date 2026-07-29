package br.com.topsdojob.v3.application.aviso;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AvisoDtos {

  private AvisoDtos() {
  }

  public record AdminItem(
      UUID id,
      String titulo,
      String descricao,
      String localExibicao,
      String localExibicaoRotulo,
      String frequenciaExibicao,
      String frequenciaExibicaoRotulo,
      String status,
      String situacao,
      boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      String criadoPorNome,
      OffsetDateTime publicadoEm,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      long versao) {
  }

  public record PublicoItem(
      UUID id,
      String titulo,
      String descricao,
      String localExibicao,
      String frequenciaExibicao,
      boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      OffsetDateTime publicadoEm) {
  }

  public record Edicao(
      String titulo,
      String descricao,
      String localExibicao,
      String frequenciaExibicao,
      Boolean permiteDispensar,
      OffsetDateTime ativoDe,
      OffsetDateTime ativoAte,
      Long versao) {
  }

  public record Versao(Long versao) {
  }

  public record Indicadores(
      long total,
      long rascunhos,
      long vigentes,
      long agendados,
      long expirados,
      long arquivados) {
  }

  public record Pagina<T>(
      List<T> itens,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean first,
      boolean last) {
  }
}
