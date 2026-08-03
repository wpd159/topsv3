'use client'

import { adminApiUrl } from '@/lib/api-contract'

export type AdminCreditoUsuario = { id: string; nome: string; email: string; saldo: number }
export type AdminCreditoSaldo = {
  usuarioId: string
  saldoProjetado: number
  saldoCalculadoMovimentos: number
  totalMovimentos: number
  consistente: boolean
}
export type AdminCreditoMovimento = {
  id: string
  tipo: string
  direcao: 'CREDITO' | 'DEBITO'
  natureza: string
  quantidade: number
  saldoAntes: number
  saldoDepois: number
  origem: string
  motivo: string | null
  administradorId: string | null
  requestId: string | null
  criadoEm: string
}
export type AdminCreditoPagina = { itens: AdminCreditoMovimento[]; total: number }
export type AdminCreditoOperacao = {
  movimentoId: string
  usuarioId: string
  natureza: string
  quantidade: number
  saldoAnterior: number
  saldoPosterior: number
  motivo: string
  requestId: string
  criadoEm: string
  idempotente: boolean
}
export type AdminPremiumOpcao = {
  id: string
  duracaoDias: number
  custoCreditos: number
  ativo: boolean
  ordemExibicao: number
}
export type AdminPremiumCatalogo = {
  id: string
  codigo: string
  nome: string
  descricao: string
  ativo: boolean
  ordemExibicao: number
  opcoes: AdminPremiumOpcao[]
}
export type AdminPremiumOpcaoWrite = {
  duracaoDias: number
  custoCreditos: number
  ativo: boolean
  ordemExibicao: number
}
export type AdminPremiumCatalogoWrite = {
  nome: string
  descricao: string
  ativo: boolean
  ordemExibicao: number
  opcoes: AdminPremiumOpcaoWrite[]
}

export type AdminPlanoCredito = {
  id: string
  codigo: string
  nome: string
  descricao: string
  quantidadeCreditos: number
  valor: number
  moeda: string
  ativo: boolean
  ordemExibicao: number
  comprasConfirmadas: number
  criadoEm: string
  atualizadoEm: string
}
export type AdminPlanoCreditoCriar = {
  codigo: string
  nome: string
  descricao: string
  quantidadeCreditos: number
  valor: number
  ativo: boolean
  ordemExibicao: number
}
export type AdminPlanoCreditoAtualizar = Omit<AdminPlanoCreditoCriar, 'codigo' | 'ativo'> & {
  atualizadoEm: string
}
export type AdminPremiumAtivacao = {
  id: string
  usuarioId: string
  anuncioId: string
  beneficioCodigo: string
  beneficioNome: string
  origem: string
  status: string
  custoCreditos: number
  inicioEm: string
  fimEm: string
}
export type AdminAuditoriaFinanceira = {
  id: string
  administradorId: string
  acao: string
  recursoTipo: string
  recursoId: string
  requestId: string
  criadoEm: string
}

function antiForgeryCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function antiForgeryHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readAntiForgeryValue() {
  if (typeof document === 'undefined') return null
  const name = antiForgeryCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function request<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    let value = readAntiForgeryValue()
    if (!value) {
      await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
      value = readAntiForgeryValue()
    }
    if (value) headers.set(antiForgeryHeaderName(), value)
  }
  const response = await fetch(adminApiUrl(path), {
    ...init,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  const raw = await response.text()
  if (!response.ok) {
    let message = `Falha na operacao (HTTP ${response.status}).`
    try {
      const body = JSON.parse(raw) as { message?: string }
      if (body.message) message = body.message
    } catch {
      // O status HTTP permanece visivel quando a resposta nao e JSON.
    }
    throw new Error(message)
  }
  return raw ? JSON.parse(raw) as T : (null as T)
}

function operationKey(prefix: string) {
  const id = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}`
  return `${prefix}-${id}`
}

export const AdminCreditosApi = {
  buscarUsuarios: (query: string) => request<AdminCreditoUsuario[]>(`/creditos/usuarios?query=${encodeURIComponent(query)}`),
  saldo: (id: string) => request<AdminCreditoSaldo>(`/creditos/usuarios/${id}/saldo`),
  movimentos: (id: string) => request<AdminCreditoPagina>(`/creditos/usuarios/${id}/movimentos?size=50`),
  ajustar: (
    id: string,
    direcao: 'CREDITO' | 'DEBITO',
    quantidade: number,
    motivo: string,
    idempotencyKey = operationKey('ajuste'),
  ) =>
    request<AdminCreditoOperacao>(`/creditos/usuarios/${id}/ajustes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify({ direcao, quantidade, motivo }),
    }),
  estornar: (movimentoId: string, motivo: string) => request<AdminCreditoOperacao>(
    `/creditos/movimentos/${movimentoId}/estornos`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operationKey('estorno') },
      body: JSON.stringify({ motivo }),
    }
  ),
  catalogo: () => request<AdminPremiumCatalogo[]>('/premium/catalogo'),

  atualizarCatalogo: (id: string, item: AdminPremiumCatalogoWrite) => request<AdminPremiumCatalogo>(
    `/premium/catalogo/${id}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item),
    }
  ),
  pacotes: (busca = '', status: 'TODOS' | 'ATIVOS' | 'INATIVOS' = 'TODOS') =>
    request<AdminPlanoCredito[]>(
      `/creditos/pacotes?busca=${encodeURIComponent(busca)}&status=${status}`,
    ),
  detalharPacote: (id: string) => request<AdminPlanoCredito>(`/creditos/pacotes/${id}`),
  criarPacote: (item: AdminPlanoCreditoCriar) => request<AdminPlanoCredito>(
    '/creditos/pacotes',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item),
    }
  ),
  atualizarPacote: (id: string, item: AdminPlanoCreditoAtualizar) => request<AdminPlanoCredito>(
    `/creditos/pacotes/${id}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(item),
    }
  ),
  ativarPacote: (id: string, atualizadoEm: string) => request<AdminPlanoCredito>(
    `/creditos/pacotes/${id}/ativacao`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ atualizadoEm }),
    }
  ),
  desativarPacote: (id: string, atualizadoEm: string) => request<AdminPlanoCredito>(
    `/creditos/pacotes/${id}/desativacao`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ atualizadoEm }),
    }
  ),
  ativacoes: (usuarioId: string) => request<AdminPremiumAtivacao[]>(`/premium/ativacoes?usuarioId=${usuarioId}`),
  cancelarAtivacao: (id: string, motivo: string) => request(`/premium/ativacoes/${id}/cancelar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operationKey('cancelamento') },
    body: JSON.stringify({ motivo }),
  }),
  auditoria: () => request<AdminAuditoriaFinanceira[]>('/creditos/auditoria?limit=50'),
}
