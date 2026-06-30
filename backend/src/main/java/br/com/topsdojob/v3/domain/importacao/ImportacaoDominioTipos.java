package br.com.topsdojob.v3.domain.importacao;

public final class ImportacaoDominioTipos {
  private ImportacaoDominioTipos() {
  }

  public enum StatusExecucao {
    PLANEJADA,
    EM_EXECUCAO,
    CONCLUIDA,
    CONCLUIDA_COM_PENDENCIAS,
    FALHA,
    CANCELADA
  }

  public enum StatusMapeamento {
    PENDENTE,
    MAPEADO,
    DIVERGENTE,
    REJEITADO
  }

  public enum SeveridadePendencia {
    INFO,
    BAIXA,
    MEDIA,
    ALTA,
    CRITICA
  }

  public enum StatusPendencia {
    ABERTA,
    EM_REVISAO,
    RESOLVIDA,
    ACEITA,
    REJEITADA
  }

  public enum StatusStaging {
    PENDENTE,
    PROCESSADO,
    PENDENTE_REVISAO,
    REJEITADO
  }
}
