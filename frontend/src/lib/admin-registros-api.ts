import { adminApiUrl, apiErrorFromResponse, ApiContractError } from '@/lib/api-contract'

export type PublicidadeRegistroResumo = {
  id: string
  anuncioId: string
  titulo: string | null
  natureza: string
  status: string
  inicioEm: string
  fimEm: string | null
  cobertura: string
  relacaoMaterial: string
}

export type PublicidadeRegistrosPagina = {
  itens: PublicidadeRegistroResumo[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type StoryRegistroResumo = {
  id: string
  storyId: string
  anuncioId: string | null
  titulo: string | null
  modoConteudo: string
  status: string
  natureza: string
  cobertura: string
  inicioEm: string
  fimEm: string
}

export type StoryRegistrosPagina = Omit<PublicidadeRegistrosPagina, 'itens'> & {
  itens: StoryRegistroResumo[]
}

export type PublicidadeRegistroVersao = {
  id: string
  numero: number
  capturadoEm: string
  vigenteDesde: string
  vigenteAte: string | null
  motivo: string
  conteudo: unknown
  conteudoSha256: string
  midias: { id: string; variante: string; mimeType: string; tamanhoBytes: number; ordem: number; arquivoUrl: string }[]
}

export type PublicidadeRegistroDetalhe = {
  id: string
  anuncioId: string
  contratanteUsuarioId: string | null
  ativacaoBeneficioId: string | null
  grupoAtivacaoId: string | null
  movimentoCreditoId: string | null
  pagamentoId: string | null
  natureza: string
  relacaoMaterial: string
  cobertura: string
  inicioEm: string
  fimEm: string | null
  retencaoAte: string | null
  encerramentoMotivo: string | null
  versoes: PublicidadeRegistroVersao[]
}

export type StoryRegistroDetalhe = Omit<PublicidadeRegistroDetalhe, 'anuncioId'> & {
  storyId: string
  anuncioId: string | null
  modoConteudo: string
}

export const FINALIDADES_ACESSO_ARQUIVO = [
  { codigo: 'AUDITORIA_INTERNA', rotulo: 'Auditoria interna' },
  { codigo: 'ATENDIMENTO_FISCALIZACAO', rotulo: 'Atendimento à fiscalização' },
  { codigo: 'APURACAO_INCIDENTE', rotulo: 'Apuração de incidente' },
] as const

export type FinalidadeAcessoArquivo = typeof FINALIDADES_ACESSO_ARQUIVO[number]['codigo']

const BASE_PATH = '/registros/publicidade'
const STORY_PATH = '/registros/stories'

function queryFinalidade(finalidade: FinalidadeAcessoArquivo) {
  if (!FINALIDADES_ACESSO_ARQUIVO.some((item) => item.codigo === finalidade)) {
    throw new Error('Selecione a finalidade da consulta privada.')
  }
  return `finalidade=${encodeURIComponent(finalidade)}`
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = ['XSRF', 'TOKEN'].join('-')
  const entry = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function csrfValue() {
  const current = readCsrfValue()
  if (current) return current
  const response = await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
  if (!response.ok) throw await apiErrorFromResponse(response)
  return readCsrfValue()
}

async function postRead(path: string, signal?: AbortSignal, accept = 'application/json') {
  const csrf = await csrfValue()
  const headers = new Headers({ Accept: accept })
  if (csrf) headers.set(['X', 'XSRF', 'TOKEN'].join('-'), csrf)
  const response = await fetch(adminApiUrl(path), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers,
    signal,
  })
  if (!response.ok) throw await apiErrorFromResponse(response)
  return response
}

function invalidPayload() {
  return new ApiContractError(
    'O serviço retornou registros incompatíveis.',
    'TECHNICAL_FAILURE',
    502,
    true,
  )
}

export async function listarRegistrosPublicidade(
  page: number,
  finalidade: FinalidadeAcessoArquivo,
  signal?: AbortSignal,
): Promise<PublicidadeRegistrosPagina> {
  const pagina = Math.max(0, Math.trunc(page) || 0)
  const response = await postRead(`${BASE_PATH}?page=${pagina}&size=20&${queryFinalidade(finalidade)}`, signal)
  const payload: unknown = await response.json()
  if (!payload || typeof payload !== 'object' || !('itens' in payload) || !Array.isArray(payload.itens)) {
    throw invalidPayload()
  }
  return payload as PublicidadeRegistrosPagina
}

export async function detalharRegistroPublicidade(
  id: string,
  finalidade: FinalidadeAcessoArquivo,
  signal?: AbortSignal,
): Promise<PublicidadeRegistroDetalhe> {
  const response = await postRead(`${BASE_PATH}/${encodeURIComponent(id)}?${queryFinalidade(finalidade)}`, signal)
  const payload: unknown = await response.json()
  if (!payload || typeof payload !== 'object' || !('id' in payload) || typeof payload.id !== 'string'
    || !('versoes' in payload) || !Array.isArray(payload.versoes)) {
    throw invalidPayload()
  }
  return payload as PublicidadeRegistroDetalhe
}

export async function exportarRegistroPublicidade(id: string, finalidade: FinalidadeAcessoArquivo): Promise<Blob> {
  const response = await postRead(`${BASE_PATH}/${encodeURIComponent(id)}/exportacao?${queryFinalidade(finalidade)}`)
  return response.blob()
}

export async function baixarMidiaPublicidade(
  registroId: string,
  midiaId: string,
  finalidade: FinalidadeAcessoArquivo,
): Promise<Blob> {
  const response = await postRead(
    `${BASE_PATH}/${encodeURIComponent(registroId)}/midias/${encodeURIComponent(midiaId)}/arquivo?${queryFinalidade(finalidade)}`,
    undefined,
    'application/octet-stream',
  )
  return response.blob()
}

export async function listarRegistrosStory(
  page: number,
  finalidade: FinalidadeAcessoArquivo,
  signal?: AbortSignal,
): Promise<StoryRegistrosPagina> {
  const pagina = Math.max(0, Math.trunc(page) || 0)
  const response = await postRead(`${STORY_PATH}?page=${pagina}&size=20&${queryFinalidade(finalidade)}`, signal)
  const payload: unknown = await response.json()
  if (!payload || typeof payload !== 'object' || !('itens' in payload) || !Array.isArray(payload.itens)) {
    throw invalidPayload()
  }
  return payload as StoryRegistrosPagina
}

export async function detalharRegistroStory(
  id: string,
  finalidade: FinalidadeAcessoArquivo,
  signal?: AbortSignal,
): Promise<StoryRegistroDetalhe> {
  const response = await postRead(`${STORY_PATH}/${encodeURIComponent(id)}?${queryFinalidade(finalidade)}`, signal)
  const payload: unknown = await response.json()
  if (!payload || typeof payload !== 'object' || !('id' in payload) || typeof payload.id !== 'string'
    || !('versoes' in payload) || !Array.isArray(payload.versoes)) {
    throw invalidPayload()
  }
  return payload as StoryRegistroDetalhe
}

export async function exportarRegistroStory(id: string, finalidade: FinalidadeAcessoArquivo): Promise<Blob> {
  const response = await postRead(`${STORY_PATH}/${encodeURIComponent(id)}/exportacao?${queryFinalidade(finalidade)}`)
  return response.blob()
}

export async function baixarMidiaStory(
  registroId: string,
  midiaId: string,
  finalidade: FinalidadeAcessoArquivo,
): Promise<Blob> {
  const response = await postRead(
    `${STORY_PATH}/${encodeURIComponent(registroId)}/midias/${encodeURIComponent(midiaId)}/arquivo?${queryFinalidade(finalidade)}`,
    undefined,
    'application/octet-stream',
  )
  return response.blob()
}
