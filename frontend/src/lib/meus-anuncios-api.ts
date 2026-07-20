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

export type MinhaMidiaGestao = {
  id: string
  tipo: 'FOTO' | 'VIDEO'
  ordem: number | null
  status: string
  visibilidadeMidia: string | null
  previewUrl: string | null
  restrita: boolean
  ocultaPorLimite: boolean
}

export type MinhasMidiasLimites = {
  maxFotos: number
  fotosAtivas: number
  fotosDisponiveis: number
  maxVideos: number
  videosAtivos: number
  videosDisponiveis: number
  fotosExtrasAtivo: boolean
  maxFotoBytes: number
  maxVideoBytes: number
}

export type MinhasMidiasResponse = {
  midias: MinhaMidiaGestao[]
  limites: MinhasMidiasLimites
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
  await fetch(publicApiUrl('/auth/me'), {
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

  const response = await fetch(publicApiUrl(path), {
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

export function listarMinhasMidias(slug: string) {
  return request<MinhasMidiasResponse>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias`
  )
}

export function consultarLimitesMinhasMidias(slug: string) {
  return request<MinhasMidiasLimites>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/limites`
  )
}

export async function enviarMinhaMidia(
  slug: string,
  arquivo: File,
  onProgress?: (percentual: number) => void
) {
  const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
  return new Promise<MinhasMidiasResponse>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', publicApiUrl(`/minha-conta/anuncios/${encodeURIComponent(slug)}/midias`))
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    if (csrfValue) xhr.setRequestHeader(csrfHeaderName(), csrfValue)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onerror = () => reject(new MeusAnunciosApiError('Não foi possível enviar a mídia.', 0))
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        body = null
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        const message = body && typeof body === 'object' && 'message' in body
          ? String((body as { message?: unknown }).message || '')
          : `Não foi possível enviar a mídia (HTTP ${xhr.status}).`
        reject(new MeusAnunciosApiError(message, xhr.status))
        return
      }
      onProgress?.(100)
      resolve(body as MinhasMidiasResponse)
    }
    const form = new FormData()
    form.append('arquivo', arquivo)
    xhr.send(form)
  })
}

export function reordenarMinhasMidias(slug: string, midiaIds: string[]) {
  return request<MinhasMidiasResponse>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/ordem`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ midiaIds }),
    }
  )
}

export function removerMinhaMidia(slug: string, midiaId: string) {
  return request<MinhasMidiasResponse>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/${encodeURIComponent(midiaId)}`,
    { method: 'DELETE' }
  )
}
import { publicApiUrl } from '@/lib/api-contract'
