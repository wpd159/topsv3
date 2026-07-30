import type { AdminKycSubmission, AdminPage } from '@/features/admin-anuncios/types'

export type AdminUserSummary = {
  id: string
  nome?: string | null
  email?: string | null
  telefone?: string | null
  cpfMascarado?: string | null
  status: string
  kycStatus: string
  totalAnuncios: number
  bloqueado: boolean
  potencialmenteExcluivel: boolean
  ufPrincipal?: string | null
  cidadePrincipal?: string | null
  criadoEm: string
}

export type AdminUserAd = {
  id: string
  slug: string
  titulo: string
  status: string
  statusModeracao: string
  criadoEm: string
  atualizadoEm?: string | null
}

export type AdminUserHistory = {
  id: string
  acao: string
  resultado: string
  requestId?: string | null
  criadoEm: string
}

export type AdminUserDetail = {
  id: string
  nome?: string | null
  nomeCivil?: string | null
  email?: string | null
  telefone?: string | null
  cpf?: string | null
  cpfMascarado: boolean
  dataNascimento?: string | null
  status: string
  tipoConta: string
  kycStatus: string
  bloqueado: boolean
  criadoEm: string
  atualizadoEm?: string | null
  anuncioAncoraBloqueioId?: string | null
  podeBloquear: boolean
  podeDesbloquear: boolean
  versao: number
  anuncios: AdminUserAd[]
  kycEnvios: AdminKycSubmission[]
  historico: AdminUserHistory[]
}

export type AdminUserFilters = {
  termo?: string
  status: string
  kyc: string
  grupo: string
  uf?: string
  cidade?: string
  ordenacao: string
  page: number
  size: number
}

export type AdminUserPage = AdminPage<AdminUserSummary>

export type AdminUserIndicators = {
  totalUsuarios: number
  novosHoje: number
  comAnuncios: number
  semAnuncios: number
}

export type AdminUserUpdate = {
  versao: number
  nome: string
  nomeCivil: string
  email: string
  cpf: string
  telefone: string
  dataNascimento: string
}

export type AdminUserDeletionEligibility = {
  podeExcluir: boolean
  bloqueios: string[]
}

export type AdminUserDeletionResult = {
  id: string
  excluido: boolean
}
