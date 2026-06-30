package br.com.topsdojob.v3.domain.seo;

public final class SeoTipos {
  private SeoTipos() {
  }

  public enum TipoUrl {
    ANUNCIO,
    CIDADE,
    BAIRRO,
    SITEMAP,
    ROBOTS,
    INSTITUCIONAL,
    BLOG,
    OUTRO
  }

  public enum StatusEsperado {
    OK_200,
    REDIRECT_301,
    NOINDEX,
    REMOVIDO,
    PENDENTE
  }

  public enum QualidadeStatus {
    PENDENTE,
    APROVADO,
    INSUFICIENTE,
    BLOQUEADO
  }

  public enum Robots {
    INDEX_FOLLOW,
    NOINDEX_FOLLOW,
    NOINDEX_NOFOLLOW
  }

  public enum OrigemMetadado {
    SISTEMA,
    ADMIN,
    IMPORTACAO,
    GERADO
  }

  public enum StatusConteudo {
    RASCUNHO,
    REVISAO,
    APROVADO,
    PUBLICADO,
    ARQUIVADO
  }

  public enum OrigemConteudo {
    ADMIN,
    IMPORTACAO,
    GERADO
  }
}
