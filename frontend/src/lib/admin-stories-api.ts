export type AdminStorySelection = {
  ativa: boolean
  anuncioId: string | null
  anuncioSlug: string | null
  anuncioTitulo: string | null
  fotosAprovadas: number
  videosAprovados: number
  ativadoEm: string | null
  ativadoPorId: string | null
  ativadoPorEmail: string | null
}

export type AdminStoryCandidate = {
  anuncioId: string
  slug: string
  titulo: string
  fotosAprovadas: number
  videosAprovados: number
  selecionado: boolean
}

export type AdminStoryCandidatePage = {
  itens: AdminStoryCandidate[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

function adminUrl(path: string) {
  return adminApiUrl(`/stories${path}`)
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

async function ensureAntiForgeryValue() {
  let value = readAntiForgeryValue()
  if (value) return value
  await fetch(adminApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  value = readAntiForgeryValue()
  return value
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const value = await ensureAntiForgeryValue()
    if (value) headers.set(antiForgeryHeaderName(), value)
  }
  const response = await fetch(adminUrl(path), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { message?: string } | null
    throw new Error(body?.message || `Não foi possível concluir a operação (${response.status}).`)
  }
  return (await response.json()) as T
}

export function fetchAdminStorySelection() {
  return request<AdminStorySelection>('/selecao')
}

export function fetchAdminStoryCandidates(termo = '') {
  const query = new URLSearchParams({ page: '0', size: '50' })
  if (termo.trim()) query.set('termo', termo.trim())
  return request<AdminStoryCandidatePage>(`/candidatos?${query.toString()}`)
}

export function activateAdminStorySelection(anuncioId: string) {
  return request<AdminStorySelection>(`/selecao/${encodeURIComponent(anuncioId)}`, { method: 'POST' })
}

export function deactivateAdminStorySelection() {
  return request<AdminStorySelection>('/selecao', { method: 'DELETE' })
}
import { adminApiUrl } from '@/lib/api-contract'
