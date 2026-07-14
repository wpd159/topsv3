import type { MidiaPublica } from '@/lib/media/public-media'

export type FavoritoPublico = {
  id: string
  slug: string
  titulo: string
  descricaoResumo?: string | null
  preco?: number | null
  localizacao: {
    uf?: string | null
    estado?: string | null
    cidade?: string | null
    cidadeSlug?: string | null
    bairro?: string | null
    bairroSlug?: string | null
    enderecoResumido?: string | null
  }
  midias: MidiaPublica[]
  contatoDisponivel: boolean
  comLocal: boolean
  fazAnal: boolean
  anunciaDesde?: string | null
  adicionadoEm: string
}

type FavoritoEstado = {
  slug: string
  favorito: boolean
}

export class FavoritosApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
    this.name = 'FavoritosApiError'
  }
}

const API = (process.env.NEXT_PUBLIC_API_URL || '/api/public').replace(/\/$/, '')

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const cookieName = ['XSRF', 'TOKEN'].join('-')
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${cookieName}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(cookieName.length + 1)) : null
}

async function csrfValue() {
  const current = readCsrfValue()
  if (current) return current
  await fetch(`${API}/auth/me`, {
    credentials: 'include',
    cache: 'no-store',
  })
  return readCsrfValue()
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const valorCsrf = await csrfValue()
    if (valorCsrf) headers.set(['X', 'XSRF', 'TOKEN'].join('-'), valorCsrf)
  }

  const response = await fetch(`${API}${path}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    let message = `Não foi possível atualizar os favoritos (HTTP ${response.status}).`
    try {
      const payload = (await response.json()) as { message?: unknown }
      if (typeof payload.message === 'string' && payload.message.trim()) message = payload.message
    } catch {
      // O status HTTP continua disponível no erro tipado.
    }
    throw new FavoritosApiError(message, response.status)
  }
  return (await response.json()) as T
}

export function listarFavoritos() {
  return request<FavoritoPublico[]>('/minha-conta/favoritos')
}

export function incluirFavorito(slug: string) {
  return request<FavoritoEstado>(`/minha-conta/favoritos/${encodeURIComponent(slug)}`, {
    method: 'PUT',
  })
}

export function removerFavorito(slug: string) {
  return request<FavoritoEstado>(`/minha-conta/favoritos/${encodeURIComponent(slug)}`, {
    method: 'DELETE',
  })
}
