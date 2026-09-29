import { adminApiUrl, apiErrorFromResponse, ApiContractError } from '@/lib/api-contract'

export type RegistroFamilia = 'publicidade' | 'stories'
export type RegistroFimTipo = 'LIMITE_PREVISTO' | 'ENCERRAMENTO_REGISTRADO' | 'SEM_TERMINO_REGISTRADO'
export type RegistroFiltros = {
  termo?: string
  anuncioId?: string
  anuncianteId?: string
  beneficio?: string
  situacao?: 'EM_VEICULACAO' | 'ENCERRADA'
  inicio?: string
  fim?: string
  ordenacao?: 'RECENTES' | 'ANTIGOS'
}

type RegistroIdentificacao = {
  slug: string | null
  anuncianteNome: string | null
  anuncianteId: string
  beneficioCodigo: string | null
  totalVersoes: number
  encerramentoMotivo: string | null
  retencaoAte: string | null
  preservacaoAtiva: boolean
  fimTipo: RegistroFimTipo
}

export type PublicidadeRegistroResumo = RegistroIdentificacao & {
  tipo: 'ANUNCIO'
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

export type StoryRegistroResumo = RegistroIdentificacao & {
  tipo: 'STORY'
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
  contratante: unknown
  comercial: unknown
  segmentacao: unknown
  alcance: unknown
  conteudoSha256: string
  midias: { id: string; variante: string; mimeType: string; tamanhoBytes: number; ordem: number; sha256: string; arquivoUrl: string }[]
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
  fimTipo: RegistroFimTipo
  preservacoes: { id: string; fundamento: string; responsavelUsuarioId: string; inicioEm: string; revisarEm: string | null }[]
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

export type RegistroRelatorio = {
  tipo: 'ANUNCIO' | 'STORY'
  geradoEm: string
  fusoHorario: string
  responsavelId: string
  finalidade: FinalidadeAcessoArquivo
  filtros: RegistroFiltros
  idsSelecionados: string[]
  quantidade: number
  limiteRegistros: number
  lacunas: string[]
  registros: (PublicidadeRegistroDetalhe | StoryRegistroDetalhe)[]
}

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

async function postRead(path: string, signal?: AbortSignal, accept = 'application/json', body?: unknown) {
  const csrf = await csrfValue()
  const headers = new Headers({ Accept: accept })
  if (csrf) headers.set(['X', 'XSRF', 'TOKEN'].join('-'), csrf)
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  const response = await fetch(adminApiUrl(path), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers,
    signal,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (!response.ok) throw await apiErrorFromResponse(response)
  return response
}

export function filtrosRegistroQuery(filtros: RegistroFiltros) {
  const query = new URLSearchParams()
  for (const field of ['termo', 'anuncioId', 'anuncianteId', 'beneficio', 'situacao', 'inicio', 'fim', 'ordenacao'] as const) {
    const value = filtros[field]?.trim()
    if (value) query.set(field, value)
  }
  const encoded = query.toString()
  return encoded ? `&${encoded}` : ''
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
  filtros: RegistroFiltros = {},
): Promise<PublicidadeRegistrosPagina> {
  const pagina = Math.max(0, Math.trunc(page) || 0)
  const response = await postRead(`${BASE_PATH}?page=${pagina}&size=20&${queryFinalidade(finalidade)}${filtrosRegistroQuery(filtros)}`, signal)
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
  filtros: RegistroFiltros = {},
): Promise<StoryRegistrosPagina> {
  const pagina = Math.max(0, Math.trunc(page) || 0)
  const response = await postRead(`${STORY_PATH}?page=${pagina}&size=20&${queryFinalidade(finalidade)}${filtrosRegistroQuery(filtros)}`, signal)
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

export async function prepararRelatorioRegistros(
  familia: RegistroFamilia,
  finalidade: FinalidadeAcessoArquivo,
  filtros: RegistroFiltros,
  ids: string[] = [],
  signal?: AbortSignal,
): Promise<RegistroRelatorio> {
  const base = familia === 'publicidade' ? BASE_PATH : STORY_PATH
  const response = await postRead(`${base}/relatorio?${queryFinalidade(finalidade)}`, signal, 'application/json', {
    filtros, ids, fusoHorario: 'America/Sao_Paulo',
  })
  const payload: unknown = await response.json()
  if (!payload || typeof payload !== 'object' || !('registros' in payload) || !Array.isArray(payload.registros)
    || !('quantidade' in payload) || payload.quantidade !== payload.registros.length
    || payload.registros.length > 100 || !('limiteRegistros' in payload) || payload.limiteRegistros !== 100
    || !('tipo' in payload) || payload.tipo !== (familia === 'stories' ? 'STORY' : 'ANUNCIO')
    || !('finalidade' in payload) || payload.finalidade !== finalidade
    || !('idsSelecionados' in payload) || !Array.isArray(payload.idsSelecionados)
    || payload.idsSelecionados.length !== ids.length || new Set(payload.idsSelecionados).size !== ids.length
    || !ids.every((id) => (payload.idsSelecionados as unknown[]).includes(id))
    || (ids.length > 0 && (payload.registros.length !== ids.length
      || !payload.registros.every((registro) => registro && typeof registro === 'object' && 'id' in registro && ids.includes(registro.id))))
    || !('geradoEm' in payload) || typeof payload.geradoEm !== 'string'
    || !('responsavelId' in payload) || typeof payload.responsavelId !== 'string'
    || !('fusoHorario' in payload) || payload.fusoHorario !== 'America/Sao_Paulo'
    || !('lacunas' in payload) || !Array.isArray(payload.lacunas)) {
    throw invalidPayload()
  }
  return payload as RegistroRelatorio
}
