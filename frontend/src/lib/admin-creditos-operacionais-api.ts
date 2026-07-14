'use client'

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
  natureza: string
  quantidade: number
  saldoAntes: number
  saldoDepois: number
  motivo: string | null
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

function backendRoot() {
  return (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '').replace(/\/api\/public$/, '')
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
      await fetch(`${backendRoot()}/api/admin/auth/me`, { credentials: 'include', cache: 'no-store' })
      value = readAntiForgeryValue()
    }
    if (value) headers.set(antiForgeryHeaderName(), value)
  }
  const response = await fetch(`${backendRoot()}${path}`, {
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
  buscarUsuarios: (query: string) => request<AdminCreditoUsuario[]>(`/api/admin/creditos/usuarios?query=${encodeURIComponent(query)}`),
  saldo: (id: string) => request<AdminCreditoSaldo>(`/api/admin/creditos/usuarios/${id}/saldo`),
  movimentos: (id: string) => request<AdminCreditoPagina>(`/api/admin/creditos/usuarios/${id}/movimentos?size=50`),
  ajustar: (id: string, direcao: 'CREDITO' | 'DEBITO', quantidade: number, motivo: string) =>
    request<AdminCreditoOperacao>(`/api/admin/creditos/usuarios/${id}/ajustes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operationKey('ajuste') },
      body: JSON.stringify({ direcao, quantidade, motivo }),
    }),
  estornar: (movimentoId: string, motivo: string) => request<AdminCreditoOperacao>(
    `/api/admin/creditos/movimentos/${movimentoId}/estornos`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operationKey('estorno') },
      body: JSON.stringify({ motivo }),
    }
  ),
  catalogo: () => request<AdminPremiumCatalogo[]>('/api/admin/premium/catalogo'),
  atualizarCatalogo: (item: AdminPremiumCatalogo) => request<AdminPremiumCatalogo>(
    `/api/admin/premium/catalogo/${item.id}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: item.nome,
        descricao: item.descricao,
        ativo: item.ativo,
        ordemExibicao: item.ordemExibicao,
        opcoes: item.opcoes.map((opcao) => ({
          duracaoDias: opcao.duracaoDias,
          custoCreditos: opcao.custoCreditos,
          ativo: opcao.ativo,
          ordemExibicao: opcao.ordemExibicao,
        })),
      }),
    }
  ),
  pacotes: () => request<AdminPlanoCredito[]>('/api/admin/creditos/pacotes'),
  atualizarPacote: (item: AdminPlanoCredito) => request<AdminPlanoCredito>(
    `/api/admin/creditos/pacotes/${item.id}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: item.nome,
        descricao: item.descricao,
        quantidadeCreditos: item.quantidadeCreditos,
        valor: item.valor,
        ativo: item.ativo,
        ordemExibicao: item.ordemExibicao,
      }),
    }
  ),
  ativacoes: (usuarioId: string) => request<AdminPremiumAtivacao[]>(`/api/admin/premium/ativacoes?usuarioId=${usuarioId}`),
  cancelarAtivacao: (id: string, motivo: string) => request(`/api/admin/premium/ativacoes/${id}/cancelar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': operationKey('cancelamento') },
    body: JSON.stringify({ motivo }),
  }),
  auditoria: () => request<AdminAuditoriaFinanceira[]>('/api/admin/creditos/auditoria?limit=50'),
}
