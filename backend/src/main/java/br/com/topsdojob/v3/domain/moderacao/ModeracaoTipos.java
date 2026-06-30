package br.com.topsdojob.v3.domain.moderacao;

public final class ModeracaoTipos {
  private ModeracaoTipos() {
  }

  public enum TipoRevisao {
    CRIACAO,
    EDICAO,
    MIDIA,
    DOCUMENTO,
    DENUNCIA
  }

  public enum StatusRevisao {
    ABERTA,
    EM_ANALISE,
    APROVADA,
    REJEITADA,
    CANCELADA
  }

  public enum AcaoMidiaRevisao {
    ADICIONAR,
    SUBSTITUIR,
    REMOVER,
    REORDENAR
  }

  public enum StatusMidiaRevisao {
    PENDENTE,
    APROVADA,
    REJEITADA,
    CANCELADA
  }

  public enum Decisao {
    APROVAR,
    REJEITAR,
    BLOQUEAR,
    SOLICITAR_AJUSTE,
    CANCELAR
  }
}
