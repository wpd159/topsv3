export type MeuAnuncioLocalizacao = {
  uf: string | null
  cidade: string | null
  cidadeSlug: string | null
  bairro: string | null
  bairroSlug: string | null
}

export type MeuAnuncioCapa = {
  urlPublica: string | null
  restrita: boolean
}

export type MeuAnuncioMidia = {
  id: string
  tipo: string | null
  finalidade: string | null
  ordem: number | null
  status: string | null
  visibilidadeMidia: string | null
  urlPublica: string | null
  restrita: boolean
}

export type MeuAnuncio = {
  id: string
  slug: string
  titulo: string
  descricao: string
  categoria: string
  preco: number | null
  whatsapp: string | null
  locaisAtendimento: string[]
  servicos: string[]
  status: string
  statusModeracao: string
  localizacao: MeuAnuncioLocalizacao | null
  capa: MeuAnuncioCapa | null
  midias: MeuAnuncioMidia[]
  atualizadoEm: string | null
}

export type MeuAnuncioAtualizacao = {
  titulo: string
  descricao: string
  categoria: string
  preco: number | null
  uf: string
  cidade: string
  bairro: string | null
  locaisAtendimento: string[]
  servicos: string[]
  whatsapp: string | null
}

export class MeusAnunciosApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
    this.name = 'MeusAnunciosApiError'
  }
}

const API = (process.env.NEXT_PUBLIC_API_URL || '/api/public').replace(/\/$/, '')

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const cookieName = csrfCookieName()
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${cookieName}${String.fromCharCode(61)}`))
  return entry ? decodeURIComponent(entry.slice(cookieName.length + 1)) : null
}

async function bootstrapCsrfValue() {
  await fetch(`${API}/auth/me`, {
    method: 'GET',
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
    const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
    if (csrfValue) headers.set(csrfHeaderName(), csrfValue)
  }

  const response = await fetch(`${API}${path}`, {
    ...init,
    method,
    credentials: 'include',
    cache: 'no-store',
    headers,
  })

  if (!response.ok) {
    let message = `Não foi possível concluir a solicitação (HTTP ${response.status}).`
    try {
      const body = (await response.json()) as { message?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
    } catch {
      // A resposta sem JSON preserva o status HTTP real no erro abaixo.
    }
    throw new MeusAnunciosApiError(message, response.status)
  }

  return (await response.json()) as T
}

export function listarMeusAnuncios() {
  return request<MeuAnuncio[]>('/minha-conta/anuncios')
}

export function buscarMeuAnuncio(slug: string) {
  return request<MeuAnuncio>(`/minha-conta/anuncios/${encodeURIComponent(slug)}`)
}

export function atualizarMeuAnuncio(slug: string, payload: MeuAnuncioAtualizacao) {
  return request<MeuAnuncio>(`/minha-conta/anuncios/${encodeURIComponent(slug)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
