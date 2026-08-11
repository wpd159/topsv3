import { adminApiUrl } from '@/lib/api-contract'

export type AdminStoryGerenciado = {
  id: string
  usuarioUsername: string | null
  modoConteudo: 'ANUNCIO' | 'MIDIA_UPLOAD'
  status: string
  statusAdministrativo: string
  ativo: boolean
  removivel: boolean
  publicadoEm: string | null
  expiraEm: string | null
  anuncioSlug: string | null
  anuncioTitulo: string | null
  estadoMidia: string | null
  falhaTecnica: boolean
  encerradoEm: string | null
  origemEncerramento: string | null
  motivoEncerramento: string | null
  direitoPreservado: boolean
}

export type AdminStoriesPagina = {
  itens: AdminStoryGerenciado[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type AdminStoryFilters = {
  page?: number
  size?: number
  usuarioId?: string
  busca?: string
}

export type AdminStoryRemocaoMotivo =
  | 'VIOLACAO_REGRAS'
  | 'DENUNCIA_PROCEDENTE'
  | 'DETERMINACAO_JURIDICA'
  | 'ERRO_TECNICO'
  | 'OUTRO'

export type AdminStoryRemocao = {
  id: string
  status: string
  encerradoEm: string
  origem: string
  motivo: string
  direitoPreservado: boolean
  repetido: boolean
}

export class AdminStoryManagementError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly requestId: string | null
  ) {
    super(message)
    this.name = 'AdminStoryManagementError'
  }
}

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = csrfCookieName()
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${name}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function ensureCsrfValue() {
  let value = readCsrfValue()
  if (value) return value
  await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
  value = readCsrfValue()
  return value
}

async function request<T>(path: string, init: RequestInit = {}) {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = await ensureCsrfValue()
    if (csrf) headers.set(csrfHeaderName(), csrf)
  }
  const response = await fetch(adminApiUrl(`/stories${path}`), {
    ...init,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null
    const requestId = response.headers.get('X-Request-Id')
    throw new AdminStoryManagementError(
      `${body?.message || `Não foi possível concluir a operação (${response.status}).`}${requestId ? ` Código de atendimento: ${requestId}.` : ''}`,
      response.status,
      requestId
    )
  }
  return await response.json() as T
}

export function fetchAdminStories({
  page = 0,
  size = 20,
  usuarioId,
  busca,
}: AdminStoryFilters = {}) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (usuarioId?.trim()) query.set('usuarioId', usuarioId.trim())
  if (busca?.trim()) query.set('busca', busca.trim())
  return request<AdminStoriesPagina>(`/gestao?${query.toString()}`)
}

export function removeAdminStory(
  storyId: string,
  motivo: AdminStoryRemocaoMotivo,
  descricao: string | null
) {
  return request<AdminStoryRemocao>(`/${encodeURIComponent(storyId)}/remover`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motivo, descricao }),
  })
}
