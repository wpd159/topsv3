package br.com.topsdojob.v3.application.admin.staff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminStaffDtos {
  private AdminStaffDtos() {
  }

  public record Resumo(
      UUID id,
      String nome,
      String email,
      String papel,
      String papelRotulo,
      String status,
      String statusRotulo,
      boolean ativo,
      boolean acessoPendente,
      OffsetDateTime criadoEm,
      OffsetDateTime atualizadoEm,
      Integer versao) {
  }

  public record Permissao(String codigo, String descricao) {
  }

  public record Historico(
      UUID id,
      String acao,
      String acaoRotulo,
      String atorNome,
      OffsetDateTime criadoEm,
      String requestId) {
  }

  public record Detalhe(
      Resumo staff,
      List<Permissao> permissoes,
      List<Historico> historico) {
  }

  public record Indicadores(long total, long ativos, long inativos, long administradores, long moderadores) {
  }

  public record Pagina<T>(
      List<T> itens,
      int pagina,
      int tamanho,
      long totalElementos,
      int totalPaginas) {
  }

  public record CriarRequest(String nome, String email, String papel, Boolean ativo) {
  }

  public record AtualizarRequest(String nome, String papel, Boolean ativo, Integer versao) {
  }
}
