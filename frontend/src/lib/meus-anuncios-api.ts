import { publicApiUrl } from '@/lib/api-contract'
import {
  parseVisualizacoesCanonicas,
  type VisualizacoesCanonicas,
} from '@/lib/visualizacoes-canonicas'

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

export type MeuAnuncioAcoes = {
  pausar: boolean
  reativar: boolean
  remover: boolean
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
  acoesPermitidas: MeuAnuncioAcoes
  visualizacoes: VisualizacoesCanonicas
}

export type MeuAnuncioCicloVida = {
  id: string
  slug: string
  status: string
  statusModeracao: string
  atualizadoEm: string | null
  acoesPermitidas: MeuAnuncioAcoes
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

function mapMeuAnuncio(payload: unknown): MeuAnuncio {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou um anuncio em formato incompativel.', 502)
  }

  const raw = payload as Omit<MeuAnuncio, 'visualizacoes' | 'acoesPermitidas'> & {
    acoesPermitidas?: unknown
    visualizacoes?: unknown
  }
  try {
    return {
      ...raw,
      acoesPermitidas: parseAcoesPermitidas(raw.acoesPermitidas),
      visualizacoes: parseVisualizacoesCanonicas(raw.visualizacoes),
    }
  } catch {
    throw new MeusAnunciosApiError('O servico retornou visualizacoes em formato incompativel.', 502)
  }
}

function parseAcoesPermitidas(payload: unknown): MeuAnuncioAcoes {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou acoes em formato incompativel.', 502)
  }
  const raw = payload as Partial<MeuAnuncioAcoes>
  if (
    typeof raw.pausar !== 'boolean' ||
    typeof raw.reativar !== 'boolean' ||
    typeof raw.remover !== 'boolean'
  ) {
    throw new MeusAnunciosApiError('O servico retornou acoes em formato incompativel.', 502)
  }
  return { pausar: raw.pausar, reativar: raw.reativar, remover: raw.remover }
}

function mapCicloVida(payload: unknown): MeuAnuncioCicloVida {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou uma transicao em formato incompativel.', 502)
  }
  const raw = payload as Partial<MeuAnuncioCicloVida> & { acoesPermitidas?: unknown }
  if (
    typeof raw.id !== 'string' ||
    typeof raw.slug !== 'string' ||
    typeof raw.status !== 'string' ||
    typeof raw.statusModeracao !== 'string' ||
    (raw.atualizadoEm !== null && typeof raw.atualizadoEm !== 'string')
  ) {
    throw new MeusAnunciosApiError('O servico retornou uma transicao em formato incompativel.', 502)
  }
  return {
    id: raw.id,
    slug: raw.slug,
    status: raw.status,
    statusModeracao: raw.statusModeracao,
    atualizadoEm: raw.atualizadoEm ?? null,
    acoesPermitidas: parseAcoesPermitidas(raw.acoesPermitidas),
  }
}

export async function listarMeusAnuncios() {
  const payload = await request<unknown>('/minha-conta/anuncios')
  if (!Array.isArray(payload)) {
    throw new MeusAnunciosApiError('O servico retornou uma listagem em formato incompativel.', 502)
  }
  return payload.map(mapMeuAnuncio)
}

export async function buscarMeuAnuncio(slug: string) {
  return mapMeuAnuncio(await request<unknown>(`/minha-conta/anuncios/${encodeURIComponent(slug)}`))
}

export async function atualizarMeuAnuncio(slug: string, payload: MeuAnuncioAtualizacao) {
  const resposta = await request<unknown>(`/minha-conta/anuncios/${encodeURIComponent(slug)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return mapMeuAnuncio(resposta)
}

export async function pausarMeuAnuncio(slug: string) {
  return mapCicloVida(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/pausar`,
    { method: 'POST' }
  ))
}

export async function reativarMeuAnuncio(slug: string) {
  return mapCicloVida(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/reativar`,
    { method: 'POST' }
  ))
}

export async function removerMeuAnuncio(slug: string) {
  return mapCicloVida(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}`,
    { method: 'DELETE' }
  ))
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

const mediaUploadIdempotencyKeys = new WeakMap<File, string>()

function mediaUploadIdempotencyKey(file: File) {
  const existing = mediaUploadIdempotencyKeys.get(file)
  if (existing) return existing
  const created = crypto.randomUUID()
  mediaUploadIdempotencyKeys.set(file, created)
  return created
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
    xhr.setRequestHeader('Idempotency-Key', mediaUploadIdempotencyKey(arquivo))
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
      mediaUploadIdempotencyKeys.delete(arquivo)
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
