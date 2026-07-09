/** Fila de revisões — espelha `AnuncioRevisionQueueItemDTO` (GET /anuncios/staff/revisions) */
export type ModerationRevisionQueueItem = {
  revisionId: number
  anuncioId: number
  anuncioTitulo?: string | null
  usernameAnunciante?: string | null
  status: string
  source?: string | null
  submittedAt?: string | null
  changedFields?: string[] | null
}

/** Lista staff — espelha `AnuncioStaffResponseDTO` */
export type ModerationStaffListItem = {
  id: number
  titulo: string
  usernameAnunciante: string
  status: string
  dataCriacao: string
  pendingRevision?: boolean | null
  pendingRevisionId?: number | null
  pendingRevisionStatus?: string | null
  removidoLogicamente?: boolean | null
  visualizacoes?: number | null
  cliquesWhatsapp?: number | null
  thumbnailUrl?: string | null
  thumbnailIsVideo?: boolean | null
  /** Valor de `ContentClassification` no anúncio (lista staff). */
  contentClassification?: string | null
  /** Nome da cidade (DTO staff; drill-down do dashboard). */
  cidadeNome?: string | null
}

export type ModerationAnuncioDetail = {
  id: number
  usuarioId?: number | null
  titulo?: string | null
  username?: string | null
  nomeCompleto?: string | null
  cpf?: string | null
  categoria?: string | null
  preco?: number | null
  horario?: string | null
  status?: string | null
  descricao?: string | null
  linkConteudo?: string | null
  contentClassification?: string | null
  slug?: string | null
  locaisAtendimento?: string[] | null
  servicos?: string[] | null
  fotosUrl?: string[] | null
  videosUrl?: string[] | null
  documentosUsuario?: string[] | null
  pendingRevision?: boolean | null
  pendingRevisionId?: number | null
  pendingRevisionStatus?: string | null
  estadoId?: number | null
  estadoNome?: string | null
  estadoUf?: string | null
  cidadeId?: number | null
  cidadeNome?: string | null
  bairroId?: number | null
  bairroNome?: string | null
  localizacaoLabel?: string | null
  cidadeAnunciante?: string | null
  removidoLogicamente?: boolean | null
  removidoLogicamenteEm?: string | null
  removidoLogicamenteMotivo?: string | null
  removidoPorStaff?: boolean | null
  /** Cadastro do anúncio (backend: dataCriacao) */
  dataCriacao?: string | null
  /** ISO-8601 com offset (ex. America/Sao_Paulo); preferir para parse estável na timeline */
  dataCriacaoIso?: string | null
  visualizacoes?: number | null
  cliquesWhatsapp?: number | null
}

export type AdminAuditLogItem = {
  id: number
  actionType: string
  entityType: string
  entityId?: string | null
  actorEmail?: string | null
  actorUserId?: number | null
  ipMasked?: string | null
  userAgentHash?: string | null
  details?: string | null
  createdAt: string
  createdAtIso?: string | null
}

export type VisitorVerificationAuditItem = {
  id: number
  eventId?: string | null
  eventType: string
  anuncioId?: number | null
  authorizationStatus?: string | null
  verificationResult?: string | null
  challengeLevel?: string | null
  challengeResult?: string | null
  decision?: string | null
  reason?: string | null
  reasonCode?: string | null
  ipMasked?: string | null
  userAgentHash?: string | null
  visitorSessionId?: string | null
  contentClassification?: string | null
  route?: string | null
  createdAt: string
  createdAtIso?: string | null
  occurredAtIso?: string | null
}

export type ModerationRevisionMediaItem = {
  id: number
  url: string
  mediaType: string
  sourceType: string
  removable: boolean
}

export type ModerationRevisionChange = {
  field: string
  label: string
  currentValue?: string | null
  pendingValue?: string | null
}

export type ModerationRevisionDetail = {
  revisionId: number
  status: string
  source: string
  submittedByEmail?: string | null
  submittedAt?: string | null
  currentContentClassification?: string | null
  pendingContentClassification?: string | null
  changedFields?: string[] | null
  changes?: ModerationRevisionChange[] | null
  pendingFotos?: string[] | null
  /** URLs de vídeo pendentes quando não há item em pendingMediaItems */
  pendingVideos?: string[] | null
  pendingMediaItems?: ModerationRevisionMediaItem[] | null
}

export type ModerationClassification =
  | 'SAFE_PUBLIC'
  | 'ADULT_NON_EXPLICIT'
  | 'ADULT_EXPLICIT_BLOCKED'
  | 'ADULT_RESTRICTED'
