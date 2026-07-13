package br.com.topsdojob.v3.persistence.shared;

public final class PersistenceEnums {
  private PersistenceEnums() {
  }

  public enum StatusUsuario {
    ATIVO, PENDENTE, SUSPENSO, DESATIVADO, IMPORTADO
  }

  public enum TipoContaUsuario {
    ANUNCIANTE, STAFF, SISTEMA
  }

  public enum PapelUsuario {
    ADMIN, MODERADOR, COMERCIAL, USUARIO
  }

  public enum StatusAnuncio {
    RASCUNHO, PENDENTE_REVISAO, APROVADO, PUBLICADO, PAUSADO, REJEITADO, REMOVIDO
  }

  public enum StatusModeracaoAnuncio {
    NAO_ENVIADO, PENDENTE, APROVADO, REJEITADO
  }

  public enum LocalAtendimentoAnuncio {
    A_COMBINAR, HOTEL_MOTEL, MEU_LOCAL
  }

  public enum ServicoAnuncio {
    ANAL,
    ATRIZ_PORNO,
    FETICHES,
    MASSAGEM_TANTRICA,
    ATIVO,
    BDSM,
    JOGOS_DE_INTERPRETACAO,
    ORAL,
    ATOR_PORNO,
    EJACULACAO_CORPORAL,
    MASSAGEM_EROTICA,
    PASSIVO,
    NAMORADAS,
    TRIO,
    VIDEOCHAMADA
  }

  public enum StatusPublicacaoBusca {
    NAO_PUBLICAVEL, PUBLICAVEL, NOINDEX, REMOVIDO
  }

  public enum StatusArquivoMidia {
    PENDENTE, VALIDADO, REJEITADO, REMOVIDO
  }

  public enum TipoAnuncioMidia {
    FOTO, VIDEO, STORY
  }

  public enum FinalidadeAnuncioMidia {
    CAPA, GALERIA, STORY
  }

  public enum StatusAnuncioMidia {
    PENDENTE, AJUSTE_SOLICITADO, PUBLICAVEL, REJEITADA, REMOVIDA
  }

  public enum StatusStoryAnuncio {
    RASCUNHO, PENDENTE, PUBLICADO, EXPIRADO, REMOVIDO
  }

  public enum TipoDocumentoUsuario {
    IDENTIDADE, VERIFICACAO_IDADE, COMPROVANTE, OUTRO
  }

  public enum ParteDocumentoUsuario {
    UNICO, FRENTE, VERSO
  }

  public enum StatusDocumentoUsuario {
    PENDENTE, EM_ANALISE, VALIDADO, REJEITADO, AJUSTE_SOLICITADO, REMOVIDO, EXPURGADO
  }

  public enum PoliticaRetencaoDocumento {
    ENQUANTO_HOUVER_ANUNCIO, DATA_DEFINIDA, RETENCAO_JURIDICA, MANUAL
  }

  public enum FinalidadeAcessoDocumento {
    VALIDACAO, AUDITORIA, SUPORTE, JURIDICO, SEGURANCA
  }

  public enum ResultadoAcessoDocumento {
    PERMITIDO, NEGADO
  }

  public enum TipoRevisaoAnuncio {
    CRIACAO, EDICAO, MIDIA, DOCUMENTO, DENUNCIA
  }

  public enum StatusRevisaoAnuncio {
    ABERTA, EM_ANALISE, APROVADA, REJEITADA, CANCELADA
  }

  public enum AcaoMidiaRevisao {
    ADICIONAR, SUBSTITUIR, REMOVER, REORDENAR
  }

  public enum StatusMidiaRevisao {
    PENDENTE, APROVADA, REJEITADA, CANCELADA
  }

  public enum DecisaoModeracao {
    APROVAR, REJEITAR, BLOQUEAR, SOLICITAR_AJUSTE, CANCELAR
  }

  public enum EscopoBeneficioPremium {
    ANUNCIO, USUARIO, MIDIA, RELATORIO
  }

  public enum TipoGrupoAtivacaoBeneficio {
    PACOTE, CAMPANHA, CORTESIA, ADMIN, IMPORTACAO
  }

  public enum OrigemBeneficio {
    COMPRA, CREDITO, CORTESIA, CAMPANHA, ADMIN, IMPORTACAO
  }

  public enum StatusGrupoAtivacaoBeneficio {
    PLANEJADO, ATIVO, EXPIRADO, REVOGADO, CANCELADO
  }

  public enum StatusAtivacaoBeneficio {
    AGENDADA, ATIVA, EXPIRADA, REVOGADA, CANCELADA
  }

  public enum TipoMovimentoCredito {
    ENTRADA, SAIDA, AJUSTE, ESTORNO
  }

  public enum DirecaoMovimentoCredito {
    CREDITO, DEBITO
  }

  public enum OrigemMovimentoCredito {
    PAGAMENTO, BENEFICIO, AJUSTE_ADMIN, ESTORNO, IMPORTACAO, CAMPANHA
  }

  public enum ProvedorPagamento {
    EFI, MERCADO_PAGO_LEGADO, OUTRO_LEGADO, DESCONHECIDO
  }

  public enum MetodoPagamento {
    PIX, LEGADO, DESCONHECIDO
  }

  public enum StatusInternoPagamento {
    CRIADO, AGUARDANDO_PAGAMENTO, APROVADO, CANCELADO, EXPIRADO, ESTORNADO, ERRO, LEGADO
  }

  public enum ValidacaoWebhook {
    PENDENTE, VALIDO, INVALIDO, IGNORADO
  }

  public enum OrigemConciliacaoPagamento {
    WEBHOOK, CONSULTA_PROVEDOR, IMPORTACAO, AJUSTE_ADMIN
  }

  public enum StatusConciliacaoPagamento {
    PENDENTE, CONCILIADO, DIVERGENTE, CANCELADO
  }

  public enum DispositivoMetrica {
    DESKTOP, MOBILE, TABLET, BOT, DESCONHECIDO
  }

  public enum ResultadoVerificacaoEtaria {
    PERMITIDO, NEGADO, INDETERMINADO
  }

  public enum MetodoVerificacaoEtaria {
    DECLARACAO, DOCUMENTO, STAFF, IMPORTACAO
  }

  public enum TipoSeoUrl {
    ANUNCIO, CIDADE, BAIRRO, SITEMAP, ROBOTS, INSTITUCIONAL, BLOG, OUTRO
  }

  public enum StatusEsperadoSeo {
    OK_200, REDIRECT_301, NOINDEX, REMOVIDO, PENDENTE
  }

  public enum QualidadeSeo {
    PENDENTE, APROVADO, INSUFICIENTE, BLOQUEADO
  }

  public enum RobotsSeo {
    INDEX_FOLLOW, NOINDEX_FOLLOW, NOINDEX_NOFOLLOW
  }

  public enum OrigemSeoMetadado {
    SISTEMA, ADMIN, IMPORTACAO, GERADO
  }

  public enum StatusSeoConteudo {
    RASCUNHO, REVISAO, APROVADO, PUBLICADO, ARQUIVADO
  }

  public enum OrigemSeoConteudo {
    ADMIN, IMPORTACAO, GERADO
  }

  public enum StatusBanner {
    RASCUNHO, AGENDADO, PUBLICADO, INATIVO, ENCERRADO
  }

  public enum OrigemAuditoria {
    ADMIN, SISTEMA, IMPORTACAO, WEBHOOK, SUPORTE
  }

  public enum ResultadoAuditoria {
    SUCESSO, NEGADO, ERRO, PENDENTE
  }

  public enum StatusOutbox {
    PENDENTE, PROCESSANDO, PROCESSADO, ERRO, CANCELADO
  }

  public enum OrigemContatoComercial {
    ORGANICO, INDICACAO, CAMPANHA, ADMIN, IMPORTACAO, OUTRO
  }

  public enum TipoInteracaoComercial {
    NOTA, LIGACAO, MENSAGEM, EMAIL, REUNIAO, CORTESIA, OUTRO
  }

  public enum StatusTicketSuporte {
    ABERTO, EM_ATENDIMENTO, AGUARDANDO_USUARIO, RESOLVIDO, ENCERRADO
  }

  public enum PrioridadeTicketSuporte {
    BAIXA, MEDIA, ALTA, CRITICA
  }

  public enum OrigemMensagemSuporte {
    USUARIO, STAFF, SISTEMA, IMPORTACAO
  }

  public enum FrequenciaBackup {
    DIARIA, SEMANAL, MENSAL, MANUAL
  }

  public enum StatusBackupExecucao {
    AGENDADO, EM_EXECUCAO, SUCESSO, FALHA, CANCELADO
  }

  public enum TipoBackupArtefato {
    BANCO, MIDIA, CONFIGURACAO, SEO, RELEASE, LOG, MANIFESTO
  }

  public enum StatusTesteRestauracao {
    PLANEJADO, EM_EXECUCAO, APROVADO, REPROVADO, CANCELADO
  }

  public enum AmbienteTesteRestauracao {
    LOCAL, STAGING, HOMOLOGACAO
  }
}
