import { publicApiUrl } from '@/lib/api-contract'
import { MeusAnunciosApiError } from '@/lib/meus-anuncios-api'

export type StoryMode = 'ANUNCIO' | 'MIDIA_UPLOAD'

export type MinhaContaStory = {
  storyId: string
  anuncioId: string | null
  modoConteudo: StoryMode
  tipoMidia: 'FOTO' | 'VIDEO' | null
  status: string
  inicioEm: string
  fimEm: string
  estadoMidia: 'DISPONIVEL' | 'INDISPONIVEL' | null
}

export type MinhaContaStoryDireito = {
  ativacaoId: string
  status: 'DISPONIVEL_PARA_PUBLICAR' | 'ATIVA'
  custoCreditosSnapshot: number | null
  inicioEm: string | null
  fimEm: string | null
}

export type MinhaContaStoryOferta = {
  modoConteudo: StoryMode
  anuncioId: string | null
  estado: 'STORY_ATIVO' | 'DIREITO_DISPONIVEL' | 'OFERTA_DISPONIVEL' | 'NOVAS_ATIVACOES_INDISPONIVEIS'
  configurada: boolean | null
  ativo: boolean | null
  duracaoHoras: 24
  custoCreditos: number | null
  saldoAtual: number | null
  saldoProjetado: number | null
  deficit: number | null
  storyAtivo: MinhaContaStory | null
  direitoDisponivel: MinhaContaStoryDireito | null
  versaoConfiguracao: number | null
}

export type MinhaContaStoryAtivacao = {
  ativacaoId: string
  modoConteudo: StoryMode
  anuncioId: string | null
  custoCreditos: number
  saldoAnterior: number
  saldoAtual: number
  duracaoHoras: 24
  idempotente: boolean
}

export type MeuStoryGerenciado = {
  id: string
  modoConteudo: StoryMode
  status: string
  publicadoEm: string | null
  expiraEm: string | null
  anuncioSlug: string | null
  anuncioTitulo: string | null
  estadoMidia: string | null
  falhaTecnica: boolean
  podeExcluir: boolean
  podeDescartar: boolean
  encerradoEm: string | null
  direitoPreservado: boolean
}

export type MeusStoriesPagina = {
  itens: MeuStoryGerenciado[]
  pagina: number
  tamanho: number
  totalElementos: number
  totalPaginas: number
}

export type MeuStoryEncerramento = {
  id: string
  status: string
  encerradoEm: string
  origem: string
  motivo: string
  direitoPreservado: boolean
  repetido: boolean
}

type PublicarStoryInput = {
  modoConteudo: StoryMode
  anuncioId?: string | null
  arquivo?: File | null
  idempotencyKey: string
  onProgress?: (percentual: number) => void
}

const STORY_ROOT = '/minha-conta/stories'

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
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    let message = `Não foi possível concluir a solicitação (HTTP ${response.status}).`
    let code: string | null = null
    try {
      const body = await response.json() as { message?: unknown; code?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
      if (typeof body.code === 'string' && body.code.trim()) code = body.code
    } catch {
      // O status HTTP continua sendo a fonte do erro quando não há JSON.
    }
    throw new MeusAnunciosApiError(message, response.status, code, response.headers.get('X-Request-Id'))
  }
  return await response.json() as T
}

function optionalDate(value: unknown) {
  return value === null || (typeof value === 'string' && Number.isFinite(Date.parse(value)))
}

function parseStory(payload: unknown, expectedMode?: StoryMode): MinhaContaStory {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O serviço retornou um Story em formato incompatível.', 502)
  }
  const story = payload as Partial<MinhaContaStory>
  const modeValid = story.modoConteudo === 'ANUNCIO' || story.modoConteudo === 'MIDIA_UPLOAD'
  const targetValid = story.modoConteudo === 'ANUNCIO'
    ? typeof story.anuncioId === 'string' && Boolean(story.anuncioId)
    : story.anuncioId === null
  const mediaValid = story.modoConteudo === 'ANUNCIO'
    ? story.tipoMidia === null
    : story.tipoMidia === 'FOTO' || story.tipoMidia === 'VIDEO'
  if (
    typeof story.storyId !== 'string' || !story.storyId ||
    !modeValid || (expectedMode && story.modoConteudo !== expectedMode) ||
    !targetValid || !mediaValid ||
    typeof story.status !== 'string' || !story.status ||
    typeof story.inicioEm !== 'string' || !Number.isFinite(Date.parse(story.inicioEm)) ||
    typeof story.fimEm !== 'string' || !Number.isFinite(Date.parse(story.fimEm)) ||
    (story.estadoMidia !== null && story.estadoMidia !== 'DISPONIVEL' && story.estadoMidia !== 'INDISPONIVEL')
  ) {
    throw new MeusAnunciosApiError('O serviço retornou um Story em formato incompatível.', 502)
  }
  return story as MinhaContaStory
}

function parseOferta(payload: unknown, mode: StoryMode, anuncioId: string | null) {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O serviço retornou uma oferta de Story incompatível.', 502)
  }
  const offer = payload as Partial<MinhaContaStoryOferta>
  const states = new Set([
    'STORY_ATIVO',
    'DIREITO_DISPONIVEL',
    'OFERTA_DISPONIVEL',
    'NOVAS_ATIVACOES_INDISPONIVEIS',
  ])
  if (
    offer.modoConteudo !== mode || offer.anuncioId !== anuncioId ||
    typeof offer.estado !== 'string' || !states.has(offer.estado) ||
    offer.duracaoHoras !== 24
  ) {
    throw new MeusAnunciosApiError('O serviço retornou uma oferta de Story incompatível.', 502)
  }
  if (offer.storyAtivo) parseStory(offer.storyAtivo, mode)
  if (offer.direitoDisponivel) {
    const right = offer.direitoDisponivel
    if (
      typeof right.ativacaoId !== 'string' || !right.ativacaoId ||
      !['DISPONIVEL_PARA_PUBLICAR', 'ATIVA'].includes(right.status) ||
      (right.custoCreditosSnapshot !== null && (!Number.isInteger(right.custoCreditosSnapshot) || right.custoCreditosSnapshot < 0)) ||
      !optionalDate(right.inicioEm) || !optionalDate(right.fimEm)
    ) {
      throw new MeusAnunciosApiError('O serviço retornou um direito de Story incompatível.', 502)
    }
  }
  return offer as MinhaContaStoryOferta
}

export async function consultarMinhaContaStoryOferta(mode: StoryMode, anuncioId: string | null) {
  const query = new URLSearchParams({ modoConteudo: mode })
  if (mode === 'ANUNCIO') {
    if (!anuncioId) throw new MeusAnunciosApiError('Selecione um anúncio para continuar.', 400)
    query.set('anuncioId', anuncioId)
  }
  return parseOferta(await request<unknown>(`${STORY_ROOT}/oferta?${query.toString()}`), mode, mode === 'ANUNCIO' ? anuncioId : null)
}

export function ativarMinhaContaStory(
  mode: StoryMode,
  anuncioId: string | null,
  custoCreditosEsperado: number,
  versaoConfiguracao: number,
  idempotencyKey: string
) {
  return request<MinhaContaStoryAtivacao>(`${STORY_ROOT}/ativacoes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({
      modoConteudo: mode,
      anuncioId: mode === 'ANUNCIO' ? anuncioId : null,
      custoCreditosEsperado,
      versaoConfiguracao,
    }),
  })
}

export function listarMeusStories(page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  return request<MeusStoriesPagina>(`${STORY_ROOT}?${query.toString()}`)
}

export function encerrarMeuStory(storyId: string) {
  return request<MeuStoryEncerramento>(`${STORY_ROOT}/${encodeURIComponent(storyId)}`, {
    method: 'DELETE',
  })
}

export async function publicarMinhaContaStory({
  modoConteudo,
  anuncioId = null,
  arquivo = null,
  idempotencyKey,
  onProgress,
}: PublicarStoryInput) {
  const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
  return new Promise<MinhaContaStory>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', publicApiUrl(STORY_ROOT))
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('Idempotency-Key', idempotencyKey)
    if (csrfValue) xhr.setRequestHeader(csrfHeaderName(), csrfValue)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(Math.min(99, Math.round((event.loaded / event.total) * 100)))
      }
    }
    xhr.onerror = () => reject(new MeusAnunciosApiError('Não foi possível publicar o Story.', 0))
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        body = null
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        const errorBody = body && typeof body === 'object' ? body as Record<string, unknown> : null
        const message = errorBody && typeof errorBody.message === 'string' && errorBody.message.trim()
          ? errorBody.message
          : `Não foi possível publicar o Story (HTTP ${xhr.status}).`
        const code = errorBody && typeof errorBody.code === 'string' && errorBody.code.trim()
          ? errorBody.code
          : null
        reject(new MeusAnunciosApiError(message, xhr.status, code, xhr.getResponseHeader('X-Request-Id')))
        return
      }
      try {
        const story = parseStory(body, modoConteudo)
        onProgress?.(100)
        resolve(story)
      } catch (cause) {
        reject(cause)
      }
    }
    const form = new FormData()
    form.append('modoConteudo', modoConteudo)
    if (modoConteudo === 'ANUNCIO' && anuncioId) form.append('anuncioId', anuncioId)
    if (modoConteudo === 'MIDIA_UPLOAD' && arquivo) form.append('arquivo', arquivo)
    onProgress?.(0)
    xhr.send(form)
  })
}
