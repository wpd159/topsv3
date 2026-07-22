export type AdminPage<T> = {
  itens: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type AdminLocation = {
  uf?: string | null
  cidade?: string | null
  bairro?: string | null
}

export type AdminAdvertiserSummary = {
  id: string
  nome?: string | null
  emailMascarado?: string | null
  status?: string | null
}

export type AdminOpenReview = {
  id: string
  tipo: string
  status: string
  criadoEm: string
}

export type AdminAdListItem = {
  id: string
  slug: string
  titulo: string
  status: string
  statusModeracao: string
  localizacao?: AdminLocation | null
  criadoEm: string
  atualizadoEm?: string | null
  publicadoEm?: string | null
  midiasTotal?: number | null
  revisoesTotal?: number | null
  contatoConfigurado: boolean
  documentoPendente: boolean
  comercialLimitado: boolean
  anunciante?: AdminAdvertiserSummary | null
  revisaoAberta?: AdminOpenReview | null
}

export type AdminAdDetail = AdminAdListItem & {
  descricaoResumo?: string | null
  descricao?: string | null
  categoria?: string | null
  ultimaPublicacaoEm?: string | null
  precoInformado: boolean
  preco?: number | null
  whatsapp?: string | null
  locaisAtendimento: string[]
  servicos: string[]
}

export type AdminMediaItem = {
  id: string
  anuncioId: string
  slugAnuncio?: string | null
  tipo: 'FOTO' | 'VIDEO'
  finalidade?: string | null
  ordem?: number | null
  status: string
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18' | null
  statusArquivo?: string | null
  mimeType?: string | null
  tamanhoBytes?: number | null
  largura?: number | null
  altura?: number | null
  duracaoMs?: number | null
  criadoEm?: string | null
  atualizadoEm?: string | null
  arquivoPrivadoOculto: boolean
}

export type AdminMediaPreview = {
  url: string
  expiraEm?: string | null
  publica: boolean
  mimeType: string
}

export type AdminModerationHistoryItem = {
  id: string
  alvoTipo: string
  alvoId: string
  acao: string
  decisao?: string | null
  motivo?: string | null
  status?: string | null
  atorId?: string | null
  requestId?: string | null
  resultado?: string | null
  criadoEm: string
}

export type AdminModerationActionResponse = {
  id: string
  recursoTipo: string
  recursoId: string
  decisao: string
  status: string
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18' | null
  auditoriaRegistrada: boolean
  emailRealEnviado: false
  hardDeleteExecutado: false
  requestId: string
  decididoEm: string
  mensagem: string
}

export type AdminAdFilters = {
  page: number
  size: number
  status?: string
  statusModeracao?: string
  uf?: string
  cidade?: string
  bairro?: string
  termo?: string
}
