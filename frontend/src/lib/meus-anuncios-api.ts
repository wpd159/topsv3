import { publicApiUrl, resolveUnsupportedPhotoUploadMessage } from '@/lib/api-contract'
import { isSupportedUploadVideo, validatePhotoUpload } from '@/lib/photo-upload-validation'
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
  enderecoResumido: string | null
}

export type MeuAnuncioCapa = {
  urlPublica: string | null
  restrita: boolean
  previewUrl?: string | null
  previewExpiraEm?: string | null
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
  corrigirEReenviar: boolean
}

export type MeuAnuncioReprovacao = {
  motivo: string
  decididoEm: string
}

export type MeuAnuncioBeneficio = {
  codigo: string
  nome: string
  status: 'DISPONIVEL_PARA_PUBLICAR' | 'AGUARDANDO_MODERACAO' | 'ATIVO' | 'EXPIRADO' | 'PENDENTE' | 'INATIVO' | 'ERRO'
  inicioEm: string | null
  fimEm: string | null
  duracaoDias: number | null
  diasRestantes: number | null
  motivoEspera: string | null
  origem: string | null
}

export type MeuAnuncioStory = {
  storyId: string
  anuncioId: string
  modoConteudo: 'ANUNCIO' | 'MIDIA_UPLOAD'
  tipoMidia: 'FOTO' | 'VIDEO' | null
  status: string
  inicioEm: string
  fimEm: string
  estadoMidia: 'DISPONIVEL' | 'INDISPONIVEL' | null
}

const EMPTY_BENEFICIOS_PREMIUM: readonly MeuAnuncioBeneficio[] = Object.freeze([])

export type MinhaMidiaGestao = {
  id: string
  tipo: 'FOTO' | 'VIDEO'
  ordem: number | null
  status: string
  visibilidadeMidia: string | null
  previewUrl: string | null
  previewExpiraEm?: string | null
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
  videoAtivo: boolean
  maxFotoBytes: number
  maxVideoBytes: number
}

export type MinhasMidiasResponse = {
  midias: MinhaMidiaGestao[]
  limites: MinhasMidiasLimites
  anuncio: MeuAnuncioCicloVida
  fotosValidasAtivasTotal: number
}

export type MeuAnuncio = {
  id: string
  slug: string
  titulo: string
  descricao: string
  categoria: string
  preco: number | null
  whatsapp: string | null
  linkConteudo: string | null
  locaisAtendimento: string[]
  servicos: string[]
  atendimentoExclusivamenteVirtual: boolean
  status: string
  statusModeracao: string
  localizacao: MeuAnuncioLocalizacao | null
  capa: MeuAnuncioCapa | null
  midias: MeuAnuncioMidia[]
  atualizadoEm: string | null
  acoesPermitidas: MeuAnuncioAcoes
  visualizacoes: VisualizacoesCanonicas
  reprovacao: MeuAnuncioReprovacao | null
  beneficiosPremium: MeuAnuncioBeneficio[]
  storyAtivo: MeuAnuncioStory | null
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
  enderecoResumido: string | null
  locaisAtendimento: string[]
  servicos: string[]
  atendimentoExclusivamenteVirtual: boolean
  linkConteudo: string | null
}

export class MeusAnunciosApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null = null,
    readonly requestId: string | null = null,
    readonly unsupportedPhotoUpload = false,
  ) {
    super(message)
    this.name = 'MeusAnunciosApiError'
  }
}

type MeusAnunciosErrorEnvelope = {
  message?: unknown
  detail?: unknown
  mensagem?: unknown
  error?: unknown
  code?: unknown
  requestId?: unknown
}

function nonBlankString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function parseErrorEnvelope(value: string): MeusAnunciosErrorEnvelope | null {
  if (!value) return null
  try {
    const parsed = JSON.parse(value) as unknown
    return parsed && typeof parsed === 'object' ? parsed as MeusAnunciosErrorEnvelope : null
  } catch {
    return null
  }
}

const PHOTO_UPLOAD_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp'])
const OTHER_VIDEO_EXTENSIONS = new Set(['avi', 'mkv', 'webm', 'm4v', 'mpeg', 'mpg', '3gp', '3g2', 'ogv', 'wmv', 'flv', 'mts', 'm2ts'])

function isVideoUploadFile(file: Pick<File, 'name' | 'type'>) {
  const extension = file.name.trim().toLowerCase().match(/\.([^.]+)$/)?.[1]
  if (extension && PHOTO_UPLOAD_EXTENSIONS.has(extension)) return false
  // The generic media adapter retains the backend's video validation. This
  // classification does not add a supported format or trust MIME for photos.
  return isSupportedUploadVideo(file)
    || Boolean(extension && OTHER_VIDEO_EXTENSIONS.has(extension))
}

function isPhotoUploadFile(file: Pick<File, 'name' | 'type'>) {
  if (isVideoUploadFile(file)) return false
  const mimeType = file.type.trim().toLowerCase()
  const extension = file.name.trim().toLowerCase().match(/\.([^.]+)$/)?.[1]
  return Boolean(extension && PHOTO_UPLOAD_EXTENSIONS.has(extension)) || mimeType.startsWith('image/')
}

function containsOnlyPhotoUploads(files: readonly Pick<File, 'name' | 'type'>[]) {
  return files.length > 0 && files.every(isPhotoUploadFile)
}

async function validateMediaUploadPhotos(files: readonly File[]) {
  const results = await Promise.all(files.map(async (file) => {
    if (isVideoUploadFile(file)) return null
    const extension = file.name.trim().toLowerCase().match(/\.([^.]+)$/)?.[1]
    if (file.type.trim().toLowerCase().startsWith('video/')
      && (!extension || !PHOTO_UPLOAD_EXTENSIONS.has(extension))) {
      return `${file.name}: O formato e o nome deste arquivo não são compatíveis com o envio. Selecione outro arquivo no formato original.`
    }
    const result = await validatePhotoUpload(file)
    return result.valid ? null : `${file.name}: ${result.message}`
  }))
  const failures = results.filter((message): message is string => message !== null)
  if (failures.length) {
    throw new MeusAnunciosApiError(failures.join('\n'), 400, 'PHOTO_UPLOAD_LOCAL_INVALID')
  }
}

function uploadErrorFromXhr(
  xhr: XMLHttpRequest,
  envelope: MeusAnunciosErrorEnvelope | null,
  fallback: string,
  unsupportedPhotoUpload: boolean,
) {
  const usefulMessage = [envelope?.message, envelope?.detail, envelope?.mensagem]
    .map(nonBlankString)
    .find(Boolean) ?? null
  const candidateMessage = usefulMessage || nonBlankString(envelope?.error)
  const message = xhr.status === 415 && unsupportedPhotoUpload
    ? resolveUnsupportedPhotoUploadMessage(candidateMessage)
    : candidateMessage || fallback
  return new MeusAnunciosApiError(
    message,
    xhr.status,
    nonBlankString(envelope?.code),
    nonBlankString(envelope?.requestId) || xhr.getResponseHeader('X-Request-Id'),
    xhr.status === 415 && unsupportedPhotoUpload,
  )
}

export function meusAnunciosErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof MeusAnunciosApiError)) {
    return error instanceof Error && error.message ? error.message : fallback
  }
  if (error.status === 415 && error.unsupportedPhotoUpload) return error.message || fallback
  return [
    error.message || fallback,
    error.code ? `Código: ${error.code}` : null,
    error.requestId ? `Request ID: ${error.requestId}` : null,
  ].filter(Boolean).join(' · ')
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
    let code: string | null = null
    let bodyRequestId: string | null = null
    try {
      const body = (await response.json()) as { message?: unknown; code?: unknown; requestId?: unknown }
      if (typeof body.message === 'string' && body.message.trim()) message = body.message
      if (typeof body.code === 'string' && body.code.trim()) code = body.code
      bodyRequestId = typeof body.requestId === 'string' && body.requestId.trim()
        ? body.requestId.trim()
        : null
    } catch {
      // A resposta sem JSON preserva o status HTTP real no erro abaixo.
    }
    throw new MeusAnunciosApiError(
      message,
      response.status,
      code,
      bodyRequestId || response.headers.get('X-Request-Id')
    )
  }

  return (await response.json()) as T
}

function mapMeuAnuncio(payload: unknown): MeuAnuncio {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou um anuncio em formato incompativel.', 502)
  }

  const raw = payload as Omit<MeuAnuncio, 'visualizacoes' | 'acoesPermitidas' | 'reprovacao' | 'beneficiosPremium' | 'storyAtivo'> & {
    acoesPermitidas?: unknown
    visualizacoes?: unknown
    reprovacao?: unknown
    beneficiosPremium?: unknown
    storyAtivo?: unknown
  }
  if (typeof raw.atendimentoExclusivamenteVirtual !== 'boolean') {
    throw new MeusAnunciosApiError('O servico retornou um anuncio em formato incompativel.', 502)
  }
  try {
    return {
      ...raw,
      acoesPermitidas: parseAcoesPermitidas(raw.acoesPermitidas),
      visualizacoes: parseVisualizacoesCanonicas(raw.visualizacoes),
      reprovacao: parseReprovacao(raw.reprovacao),
      beneficiosPremium: parseBeneficiosPremium(raw.beneficiosPremium),
      storyAtivo: parseStoryAtivo(raw.storyAtivo),
    }
  } catch {
    throw new MeusAnunciosApiError('O servico retornou um anuncio em formato incompativel.', 502)
  }
}

function parseStoryAtivo(payload: unknown): MeuAnuncioStory | null {
  if (payload == null) return null
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O serviço retornou um Story em formato incompatível.', 502)
  }
  const raw = payload as Partial<MeuAnuncioStory>
  if (
    typeof raw.storyId !== 'string' || !raw.storyId ||
    typeof raw.anuncioId !== 'string' || !raw.anuncioId ||
    (raw.modoConteudo !== 'ANUNCIO' && raw.modoConteudo !== 'MIDIA_UPLOAD') ||
    (raw.tipoMidia !== null && raw.tipoMidia !== 'FOTO' && raw.tipoMidia !== 'VIDEO') ||
    typeof raw.status !== 'string' || !raw.status ||
    typeof raw.inicioEm !== 'string' || !Number.isFinite(Date.parse(raw.inicioEm)) ||
    typeof raw.fimEm !== 'string' || !Number.isFinite(Date.parse(raw.fimEm)) ||
    (raw.estadoMidia !== null && raw.estadoMidia !== 'DISPONIVEL' && raw.estadoMidia !== 'INDISPONIVEL')
  ) {
    throw new MeusAnunciosApiError('O serviço retornou um Story em formato incompatível.', 502)
  }
  return raw as MeuAnuncioStory
}

function parseBeneficiosPremium(payload: unknown): MeuAnuncioBeneficio[] {
  if (payload == null) return EMPTY_BENEFICIOS_PREMIUM.slice()
  if (!Array.isArray(payload)) {
    throw new MeusAnunciosApiError('O servico retornou beneficios em formato incompativel.', 502)
  }
  return payload.map((item) => {
    if (!item || typeof item !== 'object') {
      throw new MeusAnunciosApiError('O servico retornou beneficios em formato incompativel.', 502)
    }
    const raw = item as Partial<MeuAnuncioBeneficio>
    const normalizado = {
      ...raw,
      inicioEm: raw.inicioEm ?? null,
      fimEm: raw.fimEm ?? null,
      duracaoDias: raw.duracaoDias ?? null,
      diasRestantes: raw.diasRestantes ?? null,
      motivoEspera: raw.motivoEspera ?? null,
      origem: raw.origem ?? null,
    }
    const statusValidos = new Set([
      'DISPONIVEL_PARA_PUBLICAR', 'AGUARDANDO_MODERACAO', 'ATIVO', 'EXPIRADO', 'PENDENTE', 'INATIVO', 'ERRO',
    ])
    if (
      typeof normalizado.codigo !== 'string' || !normalizado.codigo.trim() ||
      typeof normalizado.nome !== 'string' || !normalizado.nome.trim() ||
      typeof normalizado.status !== 'string' || !statusValidos.has(normalizado.status) ||
      !dataOpcionalValida(normalizado.inicioEm) ||
      !dataOpcionalValida(normalizado.fimEm) ||
      !inteiroOpcionalValido(normalizado.duracaoDias) ||
      !inteiroOpcionalValido(normalizado.diasRestantes) ||
      !textoOpcionalValido(normalizado.motivoEspera) ||
      !textoOpcionalValido(normalizado.origem)
    ) {
      throw new MeusAnunciosApiError('O servico retornou beneficios em formato incompativel.', 502)
    }
    return normalizado as MeuAnuncioBeneficio
  })
}

function dataOpcionalValida(value: unknown) {
  return value === null || (typeof value === 'string' && Number.isFinite(Date.parse(value)))
}

function inteiroOpcionalValido(value: unknown) {
  return value === null || (typeof value === 'number' && Number.isInteger(value) && value >= 0)
}

function textoOpcionalValido(value: unknown) {
  return value === null || typeof value === 'string'
}

function parseAcoesPermitidas(payload: unknown): MeuAnuncioAcoes {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou acoes em formato incompativel.', 502)
  }
  const raw = payload as Partial<MeuAnuncioAcoes>
  if (
    typeof raw.pausar !== 'boolean' ||
    typeof raw.reativar !== 'boolean' ||
    typeof raw.remover !== 'boolean' ||
    typeof raw.corrigirEReenviar !== 'boolean'
  ) {
    throw new MeusAnunciosApiError('O servico retornou acoes em formato incompativel.', 502)
  }
  return {
    pausar: raw.pausar,
    reativar: raw.reativar,
    remover: raw.remover,
    corrigirEReenviar: raw.corrigirEReenviar,
  }
}

function parseReprovacao(payload: unknown): MeuAnuncioReprovacao | null {
  if (payload == null) return null
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou uma reprovacao em formato incompativel.', 502)
  }
  const raw = payload as Partial<MeuAnuncioReprovacao>
  if (
    typeof raw.motivo !== 'string' ||
    !raw.motivo.trim() ||
    typeof raw.decididoEm !== 'string' ||
    !Number.isFinite(Date.parse(raw.decididoEm))
  ) {
    throw new MeusAnunciosApiError('O servico retornou uma reprovacao em formato incompativel.', 502)
  }
  return { motivo: raw.motivo, decididoEm: raw.decididoEm }
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

function mapMinhasMidias(payload: unknown, slug: string): MinhasMidiasResponse {
  if (!payload || typeof payload !== 'object') {
    throw new MeusAnunciosApiError('O servico retornou as midias em formato incompativel. Atualize a pagina para conferir o estado do anuncio.', 502, 'MIDIAS_ESTADO_NAO_CONFIRMADO')
  }
  const raw = payload as Partial<MinhasMidiasResponse>
  if (!Array.isArray(raw.midias) || !raw.limites || typeof raw.limites !== 'object'
    || !Number.isSafeInteger(raw.fotosValidasAtivasTotal) || raw.fotosValidasAtivasTotal! < 0) {
    throw new MeusAnunciosApiError('O servico retornou as midias em formato incompativel. Atualize a pagina para conferir o estado do anuncio.', 502, 'MIDIAS_ESTADO_NAO_CONFIRMADO')
  }
  let anuncio: MeuAnuncioCicloVida
  try { anuncio = mapCicloVida(raw.anuncio) }
  catch { throw new MeusAnunciosApiError('O servico não confirmou o estado do anuncio após a operação. Atualize a pagina para conferir.', 502, 'MIDIAS_ESTADO_NAO_CONFIRMADO') }
  if (anuncio.slug !== slug || !anuncio.id.trim()
    || !['RASCUNHO', 'PENDENTE_REVISAO', 'APROVADO', 'PUBLICADO', 'PAUSADO', 'REJEITADO', 'BLOQUEADO', 'REMOVIDO'].includes(anuncio.status)
    || raw.midias.some((item) => !item || typeof item.id !== 'string' || !['FOTO', 'VIDEO'].includes(item.tipo))
    || raw.fotosValidasAtivasTotal! > raw.midias.filter((item) => item.tipo === 'FOTO').length) {
    throw new MeusAnunciosApiError('O servico retornou um estado de midias incompativel. Atualize a pagina para conferir o anuncio.', 502, 'MIDIAS_ESTADO_NAO_CONFIRMADO')
  }
  return {
    midias: raw.midias,
    limites: raw.limites,
    anuncio,
    fotosValidasAtivasTotal: raw.fotosValidasAtivasTotal!,
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

export async function listarMinhasMidias(slug: string) {
  return mapMinhasMidias(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias`
  ), slug)
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
  await validateMediaUploadPhotos([arquivo])
  const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
  const unsupportedPhotoUpload = containsOnlyPhotoUploads([arquivo])
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
    xhr.onerror = () => reject(uploadErrorFromXhr(
      xhr,
      parseErrorEnvelope(xhr.responseText),
      'Não foi possível enviar a mídia.',
      unsupportedPhotoUpload,
    ))
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        body = null
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(uploadErrorFromXhr(
          xhr,
          body && typeof body === 'object' ? body as MeusAnunciosErrorEnvelope : null,
          `Não foi possível enviar a mídia (HTTP ${xhr.status}).`,
          unsupportedPhotoUpload,
        ))
        return
      }
      try {
        const result = mapMinhasMidias(body, slug)
        onProgress?.(100)
        mediaUploadIdempotencyKeys.delete(arquivo)
        resolve(result)
      } catch (error) {
        reject(error)
      }
    }
    const form = new FormData()
    form.append('arquivo', arquivo)
    xhr.send(form)
  })
}

const mediaBatchIdempotencyKeys = new Map<string, string>()
const mediaBatchFileIds = new WeakMap<File, string>()

function mediaBatchSignature(files: File[]) {
  return files
    .map((file) => {
      let identity = mediaBatchFileIds.get(file)
      if (!identity) {
        identity = crypto.randomUUID()
        mediaBatchFileIds.set(file, identity)
      }
      return identity
    })
    .join('|')
}

export async function enviarMinhasMidiasEmLote(
  slug: string,
  arquivos: File[],
  onProgress?: (percentual: number) => void
) {
  if (!arquivos.length) throw new MeusAnunciosApiError('Selecione ao menos um arquivo.', 400)
  await validateMediaUploadPhotos(arquivos)
  const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
  const signature = mediaBatchSignature(arquivos)
  const idempotencyKey = mediaBatchIdempotencyKeys.get(signature) || crypto.randomUUID()
  mediaBatchIdempotencyKeys.set(signature, idempotencyKey)
  const unsupportedPhotoUpload = containsOnlyPhotoUploads(arquivos)
  return new Promise<MinhasMidiasResponse>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', publicApiUrl(`/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/lote`))
    xhr.withCredentials = true
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('Idempotency-Key', idempotencyKey)
    if (csrfValue) xhr.setRequestHeader(csrfHeaderName(), csrfValue)
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(Math.round((event.loaded / event.total) * 100))
    }
    xhr.onerror = () => reject(uploadErrorFromXhr(
      xhr,
      parseErrorEnvelope(xhr.responseText),
      'Não foi possível enviar as mídias.',
      unsupportedPhotoUpload,
    ))
    xhr.onload = () => {
      let body: unknown = null
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : null
      } catch {
        body = null
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(uploadErrorFromXhr(
          xhr,
          body && typeof body === 'object' ? body as MeusAnunciosErrorEnvelope : null,
          `Não foi possível enviar as mídias (HTTP ${xhr.status}).`,
          unsupportedPhotoUpload,
        ))
        return
      }
      try {
        const result = mapMinhasMidias(body, slug)
        onProgress?.(100)
        mediaBatchIdempotencyKeys.delete(signature)
        resolve(result)
      } catch (error) {
        reject(error)
      }
    }
    const form = new FormData()
    arquivos.forEach((arquivo) => form.append('arquivos', arquivo))
    xhr.send(form)
  })
}

export async function reordenarMinhasMidias(slug: string, midiaIds: string[]) {
  return mapMinhasMidias(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/ordem`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ midiaIds }),
    }
  ), slug)
}

export async function removerMinhaMidia(slug: string, midiaId: string) {
  return mapMinhasMidias(await request<unknown>(
    `/minha-conta/anuncios/${encodeURIComponent(slug)}/midias/${encodeURIComponent(midiaId)}`,
    { method: 'DELETE' }
  ), slug)
}
