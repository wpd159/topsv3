'use client'

import type { AnuncioEditAPI } from '@/components/anuncios/editar/types'

export type MonetizacaoModoAtivacao =
  | 'IMPULSIONAMENTO'
  | 'FEATURE'
  | 'FEATURE_CATALOGO'
  | 'PACOTE'
  | 'STORY'
  | 'STORY_UPLOAD'

export type MonetizacaoOpcaoCodigo =
  | 'ANUNCIO_TOPO'
  | 'FOTOS_EXTRA'
  | 'VIDEO'
  | 'STORIES'
  | 'WHATSAPP_DESTACADO'
  | 'CARROSSEL'
  | 'OCULTAR_IDADE'
  | 'PACOTE_RECOMENDADO'

export type MonetizacaoCotacaoDuracao = {
  dias: number
  duracaoHoras: number
  creditos: number
  saldoSuficiente: boolean
  creditosFaltantes: number
  podeAtivar: boolean
  precisaComprarCreditos: boolean
  motivoBloqueio?: string | null
}

export type MonetizacaoCotacaoOpcao = {
  codigo: MonetizacaoOpcaoCodigo
  titulo: string
  descricao: string
  disponivel: boolean
  motivoIndisponibilidade?: string | null
  modoAtivacao: MonetizacaoModoAtivacao
  componentes: string[]
  duracoes: MonetizacaoCotacaoDuracao[]
}

export type MonetizacaoCotacaoResponse = {
  anuncioId: number
  saldoCreditos: number
  opcoes: MonetizacaoCotacaoOpcao[]
}

export type PlanoCredito = {
  id: number
  nome: string
  creditos: number
  valor: number
  descricao: string
}

export type FeatureAtivaResumo = {
  codigo: string
  nome?: string | null
  expiraEm?: unknown
}

export type AnuncioMeuResumo = {
  id: number
  slug: string
  titulo: string
  fotoCapa?: string | null
  localizacao?: string | null
  valor: string
  status?: string | null
  impulsionado?: boolean
  dataFimImpulsionamento?: string | null
  featuresAtivas?: FeatureAtivaResumo[]
}

export type MonetizacaoWizardData = {
  anuncio: AnuncioMeuResumo
  anuncioEdit: AnuncioEditAPI
  cotacao: MonetizacaoCotacaoResponse
  planos: PlanoCredito[]
}

export type CheckoutData = {
  pagamentoId: number
  planoId: number
  planoNome: string
  creditos: number
  valor: number
  provider: string
  metodoPagamento: string
  txid: string
  status: string
  statusEfetivo: string
  pixCopiaECola: string
  pixQrCode?: string | null
  expiracao?: string | null
  criadoEm?: string | null
}

export type MonetizacaoWizardStepId =
  | 'anuncio'
  | 'beneficios'
  | 'resumo'
  | 'pagamento'
  | 'sucesso'

export type MonetizacaoSelectionMode = 'pacotes' | 'individuais'

export type MonetizacaoActivationResult = {
  itens: Array<{
    codigo: MonetizacaoOpcaoCodigo
    titulo: string
    dias: number
    creditos: number
  }>
  totalCreditos: number
}
