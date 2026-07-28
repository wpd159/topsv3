export type AdminStaffSummary = {
  id: string
  nome: string
  email: string
  papel: 'ADMIN' | 'MODERADOR' | 'COMERCIAL' | 'SEM_PAPEL'
  papelRotulo: string
  status: string
  statusRotulo: string
  ativo: boolean
  acessoPendente: boolean
  criadoEm: string
  atualizadoEm: string
  versao: number
}

export type AdminStaffPermission = {
  codigo: string
  descricao: string
}

export type AdminStaffHistory = {
  id: string
  acao: string
  acaoRotulo: string
  atorNome: string | null
  criadoEm: string
  requestId: string | null
}

export type AdminStaffDetail = {
  staff: AdminStaffSummary
  permissoes: AdminStaffPermission[]
  historico: AdminStaffHistory[]
}

export type AdminStaffPage = {
  itens: AdminStaffSummary[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type AdminStaffIndicators = {
  total: number
  ativos: number
  inativos: number
  administradores: number
  moderadores: number
}

export type AdminStaffCreate = {
  nome: string
  email: string
  papel: 'ADMIN' | 'MODERADOR'
  ativo: boolean
}

export type AdminStaffUpdate = {
  nome: string
  papel: 'ADMIN' | 'MODERADOR'
  ativo: boolean
  versao: number
}
