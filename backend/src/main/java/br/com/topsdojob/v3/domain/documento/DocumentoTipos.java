package br.com.topsdojob.v3.domain.documento;

public final class DocumentoTipos {
  private DocumentoTipos() {
  }

  public enum TipoDocumento {
    IDENTIDADE,
    VERIFICACAO_IDADE,
    COMPROVANTE,
    OUTRO
  }

  public enum StatusDocumento {
    PENDENTE,
    EM_ANALISE,
    VALIDADO,
    REJEITADO,
    REMOVIDO,
    EXPURGADO
  }

  public enum PoliticaRetencao {
    ENQUANTO_HOUVER_ANUNCIO,
    DATA_DEFINIDA,
    RETENCAO_JURIDICA,
    MANUAL
  }

  public enum FinalidadeAcesso {
    VALIDACAO,
    AUDITORIA,
    SUPORTE,
    JURIDICO,
    SEGURANCA
  }

  public enum ResultadoAcesso {
    PERMITIDO,
    NEGADO
  }
}
