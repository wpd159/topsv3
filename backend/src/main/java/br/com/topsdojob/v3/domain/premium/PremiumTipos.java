package br.com.topsdojob.v3.domain.premium;

public final class PremiumTipos {
  private PremiumTipos() {
  }

  public enum EscopoBeneficio {
    ANUNCIO,
    USUARIO,
    MIDIA,
    RELATORIO
  }

  public enum TipoGrupoAtivacao {
    PACOTE,
    CAMPANHA,
    CORTESIA,
    ADMIN,
    IMPORTACAO
  }

  public enum OrigemBeneficio {
    COMPRA,
    CREDITO,
    CORTESIA,
    CAMPANHA,
    ADMIN,
    IMPORTACAO
  }

  public enum StatusGrupoAtivacao {
    PLANEJADO,
    ATIVO,
    EXPIRADO,
    REVOGADO,
    CANCELADO
  }

  public enum StatusAtivacao {
    AGENDADA,
    ATIVA,
    EXPIRADA,
    REVOGADA,
    CANCELADA
  }
}
