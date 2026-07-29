'use client'

import type { AnuncioEditAPI } from '@/components/anuncios/editar/types'

export type MonetizacaoOpcaoCodigo = string

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
  modoAtivacao: 'COMPRA_CREDITOS'
  componentes: string[]
  duracoes: MonetizacaoCotacaoDuracao[]
}

export type MonetizacaoCotacaoResponse = {
  anuncioId: string
  saldoCreditos: number
  opcoes: MonetizacaoCotacaoOpcao[]
}

export type PlanoCredito = {
  id: string
  codigo: string
  nome: string
  descricao: string
  quantidadeCreditos: number
  valor: number
  moeda: string
  ativo: boolean
  ordemExibicao: number
}

export type FeatureAtivaResumo = {
  codigo: string
  nome?: string | null
  expiraEm?: unknown
}

export type AnuncioMeuResumo = {
  id: string
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

export type CreditoMovimentoResumo = {
  id: string
  natureza: string
  quantidade: number
  saldoAnterior: number
  saldoPosterior: number
  motivo: string | null
  criadoEm: string
}

export type BeneficioAtivoResumo = {
  id: string
  anuncioId: string
  anuncioSlug?: string | null
  anuncioTitulo?: string | null
  beneficioCodigo: string
  beneficioNome: string
  status: string
  custoCreditos: number
  duracaoDias: number
  inicioEm: string
  fimEm: string
  efeitoPublico: string
  motivoIneficacia?: string | null
}

export type MonetizacaoWizardData = {
  anuncio: AnuncioMeuResumo
  anuncioEdit: AnuncioEditAPI
  cotacao: MonetizacaoCotacaoResponse
  planos: PlanoCredito[]
  historico: CreditoMovimentoResumo[]
  beneficiosAtivos: BeneficioAtivoResumo[]
}

export type MonetizacaoWizardStepId =
  | 'anuncio'
  | 'beneficios'
  | 'resumo'
  | 'sucesso'

export type MonetizacaoSelectionMode = 'individuais'

export type MonetizacaoActivationResult = {
  itens: BeneficioAtivoResumo[]
  totalCreditos: number
  saldoAnterior: number
  saldoPosterior: number
  idempotente: boolean
}

export type CompraPremiumResultado = {
  operacaoId: string
  saldoAnterior: number
  saldoPosterior: number
  totalDebitado: number
  ativacoes: BeneficioAtivoResumo[]
  idempotente: boolean
}
