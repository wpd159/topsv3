package br.com.topsdojob.v3.domain.compliance;

public final class ComplianceVisitorTypes {

  private ComplianceVisitorTypes() {
  }

  public enum NivelAcessoVisitante {
    NONE,
    LIGHT,
    REINFORCED,
    STRONG
  }

  public enum EscopoConteudoVisitante {
    MIDIA_RESTRITA,
    WHATSAPP,
    STORY,
    CONTEUDO_EXPLICITO
  }

  public enum StatusChallengeVisitante {
    ACTIVE,
    VERIFIED,
    FAILED,
    BLOCKED,
    EXPIRED,
    DOCUMENT_PENDING,
    DOCUMENT_APPROVED,
    DOCUMENT_REJECTED
  }

  public enum DecisaoRiscoVisitante {
    ALLOW_LEVEL_1,
    REQUIRE_LEVEL_2,
    REQUIRE_LEVEL_3,
    REVIEW_FLAG,
    TEMP_BLOCK,
    HARD_BLOCK
  }

  public enum EscopoTokenVisitante {
    GENERAL,
    EXPLICIT
  }

  public enum StatusTokenVisitante {
    ACTIVE,
    EXPIRED,
    REVOKED
  }

  public enum StatusDocumentoVisitante {
    PENDING,
    APPROVED,
    REJECTED
  }

  public enum EstadoPublicoAgeGate {
    GLOBAL_NAO_ACEITO,
    GLOBAL_ACEITO,
    CHALLENGE_ACTIVE,
    DADOS_NASCIMENTO,
    CPF_E_ACEITES,
    VERIFIED,
    RETRY,
    BLOCKED,
    EXPIRED,
    DOCUMENT_PENDING,
    DOCUMENT_APPROVED,
    DOCUMENT_REJECTED
  }
}
