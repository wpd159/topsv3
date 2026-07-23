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
  enderecoResumido?: string | null
}

export type AdminAdvertiserSummary = {
  id: string
  nome?: string | null
  nomeCivil?: string | null
  email?: string | null
  whatsapp?: string | null
  status?: string | null
}

export type AdminFilterLocation = {
  uf: string
  estado: string
  cidade: string
  cidadeSlug: string
  bairro?: string | null
  bairroSlug?: string | null
}

export type AdminAdvertiserDetail = {
  id: string
  nome?: string | null
  nomeCivil?: string | null
  email?: string | null
  cpf?: string | null
  whatsapp?: string | null
  status?: string | null
}

export type AdminCanonicalViews = {
  total: number | null
  situacao: 'DISPONIVEL' | 'ZERO_LEGITIMO' | 'HISTORICO_PENDENTE'
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
  miniaturaUrl?: string | null
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
  beneficiosPremiumVigentes: string[]
  beneficiosPremium: AdminQueuePremiumBenefit[]
  visualizacoes: AdminCanonicalViews
  cliquesWhatsapp: number
  anunciante?: AdminAdvertiserSummary | null
  revisaoAberta?: AdminOpenReview | null
}

export type AdminQueuePremiumBenefit = {
  ativacaoId: string
  codigo: string
  nome: string
  status: string
  inicioEm?: string | null
  fimEm?: string | null
}

export type AdminAdMetrics = {
  visualizacoes: AdminCanonicalViews
  cliquesWhatsapp: number
  ctr?: number | null
  beneficiosPremiumVigentes: string[]
  ultimaAcaoAdministrativa?: AdminModerationHistoryItem | null
}

export type AdminAdDetail = Omit<AdminAdListItem, 'anunciante' | 'miniaturaUrl' | 'beneficiosPremiumVigentes' | 'visualizacoes' | 'cliquesWhatsapp'> & {
  descricaoResumo?: string | null
  descricao?: string | null
  categoria?: string | null
  ultimaPublicacaoEm?: string | null
  precoInformado: boolean
  preco?: number | null
  whatsapp?: string | null
  locaisAtendimento: string[]
  servicos: string[]
  anunciante?: AdminAdvertiserDetail | null
  metricas: AdminAdMetrics
  bloqueioJuridico?: AdminLegalBlock | null
}

export type AdminLegalBlockCategory =
  | 'DENUNCIA_GRAVE'
  | 'USO_NAO_AUTORIZADO_IMAGEM'
  | 'FRAUDE'
  | 'ORDEM_OU_RISCO_JURIDICO'
  | 'OUTRA_INTERVENCAO'

export type AdminLegalBlock = {
  id: string
  anuncioId: string
  usuarioId: string
  escopo: 'ANUNCIO' | 'ANUNCIO_E_USUARIO'
  categoria: AdminLegalBlockCategory
  motivo: string
  observacaoInterna?: string | null
  bloqueadoPorId: string
  bloqueadoPorNome?: string | null
  bloqueadoEm: string
  anuncioBloqueado: boolean
  usuarioBloqueado: boolean
}

export type AdminLegalOperationResponse = {
  anuncioId: string
  statusAnuncio: string
  usuarioId: string
  statusUsuario: string
  acao: string
  anunciosPausados: number
  storiesSuspensos: number
  storyAdministrativoSuspenso: boolean
  executadoEm: string
}

export type AdminAdRemovalResponse = {
  anuncioId: string
  statusAnuncio: 'REMOVIDO'
  statusModeracao: string
  acao: 'REMOVER'
  midiasRemovidas: number
  objetosR2Excluidos: number
  objetosR2JaAusentes: number
  objetosCompartilhadosPreservados: number
  storiesEncerrados: number
  storyAdministrativoEncerrado: boolean
  removidoEm: string
  executadoEm: string
}

export type AdminKycDocument = {
  id: string
  parte: 'UNICO' | 'FRENTE' | 'VERSO'
  status: string
  mimeType?: string | null
  tamanhoBytes: number
}

export type AdminKycSubmission = {
  envioId: string
  usuarioId: string
  nomeCivil?: string | null
  cpfMascarado?: string | null
  dataNascimento?: string | null
  status: string
  motivo?: string | null
  enviadoEm?: string | null
  revisadoEm?: string | null
  documentos: AdminKycDocument[]
}

export type AdminKycTemporaryUrl = {
  url: string
  expiraEm: string
}

export type AdminPremiumBenefit = {
  id: string
  beneficioCodigo?: string | null
  beneficioNome?: string | null
  escopo?: string | null
  statusOriginal?: string | null
  statusCalculado: string
  origem?: string | null
  inicioEm?: string | null
  fimEm?: string | null
  duracaoDias?: number | null
  venceEmBreve: boolean
  grupoVinculado: boolean
  grupoId?: string | null
  grupoTipo?: string | null
  grupoStatus?: string | null
  grupoFimEm?: string | null
  observacao?: string | null
  codigosConsistencia: string[]
  inconsistente: boolean
  somenteLeitura: boolean
}

export type AdminPremiumCatalogOption = {
  id: string
  duracaoDias: number
  custoCreditos: number
  ativo: boolean
  ordemExibicao: number
}

export type AdminPremiumCatalogItem = {
  id: string
  codigo: string
  nome: string
  descricao?: string | null
  escopo: string
  afetaRanking: boolean
  ativo: boolean
  ordemExibicao: number
  opcoes: AdminPremiumCatalogOption[]
}

export type AdminStorySelection = {
  ativa: boolean
  anuncioId?: string | null
  anuncioSlug?: string | null
  anuncioTitulo?: string | null
  fotosAprovadas: number
  videosAprovados: number
  ativadoEm?: string | null
  expiraEm?: string | null
  classificacao: 'RESTRITA_18'
  ativadoPorId?: string | null
  ativadoPorEmail?: string | null
}

export type AdminAdUpdate = {
  titulo: string
  descricao: string
  categoria: string
  preco: number | null
  uf: string
  cidade: string
  bairro: string | null
  enderecoResumido: string | null
  locaisAtendimento: string[]
  servicos: string[]
  whatsapp: string | null
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
  categoria?: string | null
  observacaoInterna?: string | null
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
  situacao: AdminAdSituation
  ordenacao: string
  uf?: string
  cidade?: string
  bairro?: string
  termo?: string
}

export type AdminAdSituation =
  | 'TODOS'
  | 'PENDENTES_MODERACAO'
  | 'PAUSADOS'
  | 'REJEITADOS'
  | 'BLOQUEADOS'

export type AdminAdQueueTarget = {
  id: string
  page: number
}

export type AdminAdQueueNavigation = {
  anterior: AdminAdQueueTarget | null
  proximo: AdminAdQueueTarget | null
  posicao: number
  total: number
  page: number
}
