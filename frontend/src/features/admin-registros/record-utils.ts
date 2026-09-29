import type { AdminSession } from '@/lib/admin-auth-api'
import { ApiContractError } from '@/lib/api-contract'
import {
  FINALIDADES_ACESSO_ARQUIVO, filtrosRegistroQuery,
  type FinalidadeAcessoArquivo, type RegistroFamilia, type RegistroFiltros,
} from '@/lib/admin-registros-api'

export type RegistroSessionState =
  | { status: 'LOADING' | 'EXPIRED' | 'DENIED' }
  | { status: 'ERROR'; error: unknown }
  | { status: 'READY'; session: AdminSession }

export type RegistroSelecao = { escopo: 'todos' | 'selecionados'; ids: string[]; usuarioId: string | null; invalida: boolean }
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function selectionState(query: URLSearchParams): RegistroSelecao {
  const raw = query.getAll('id'), scope = query.get('escopo'), owner = query.get('selecionador')
  const escopo = scope === 'todos' && !raw.length ? 'todos' : scope === 'selecionados' || raw.length || scope ? 'selecionados' : 'todos'
  const ids = [...new Set(raw.filter((value) => UUID.test(value)))]
  return { escopo, ids, usuarioId: owner && UUID.test(owner) ? owner : null,
    invalida: query.get('selecaoInvalida') === '1' || raw.some((value) => !UUID.test(value)) || raw.length !== ids.length
      || ids.length > 100 || (!!scope && scope !== 'todos' && scope !== 'selecionados') || (scope === 'todos' && !!raw.length) }
}

export function selectionAllowed(selection: RegistroSelecao, state: RegistroSessionState) {
  return !selection.invalida && (selection.escopo === 'todos' || (selection.ids.length > 0 && selection.ids.length <= 100
    && state.status === 'READY' && selection.usuarioId === state.session.usuarioId))
}

export function emptySelection(): RegistroSelecao {
  return { escopo: 'selecionados', ids: [], usuarioId: null, invalida: true }
}

export function sessionState(session: AdminSession | null): RegistroSessionState {
  if (!session?.autenticado) return { status: 'EXPIRED' }
  if (!session.papeis.includes('ADMIN') || !session.permissoes.includes('ARQUIVO_PUBLICIDADE_LER')) return { status: 'DENIED' }
  return { status: 'READY', session }
}

export function queryState(query: URLSearchParams): { finalidade: FinalidadeAcessoArquivo | ''; filtros: RegistroFiltros; familia: RegistroFamilia; page: number } {
  const rawPurpose = query.get('finalidade')
  const finalidade: FinalidadeAcessoArquivo | '' = FINALIDADES_ACESSO_ARQUIVO.some((item) => item.codigo === rawPurpose)
    ? rawPurpose as FinalidadeAcessoArquivo : ''
  const filtros: RegistroFiltros = {}
  for (const name of ['termo', 'anuncioId', 'anuncianteId', 'beneficio', 'inicio', 'fim'] as const) {
    const value = query.get(name)?.trim()
    if (value) filtros[name] = value
  }
  const situacao = query.get('situacao')
  if (situacao === 'EM_VEICULACAO' || situacao === 'ENCERRADA') filtros.situacao = situacao
  const ordenacao = query.get('ordenacao')
  if (ordenacao === 'RECENTES' || ordenacao === 'ANTIGOS') filtros.ordenacao = ordenacao
  const rawPage = Number(query.get('page'))
  return { finalidade, filtros, familia: query.get('tipo') === 'stories' ? 'stories' as const : 'publicidade' as const,
    page: Number.isFinite(rawPage) ? Math.max(0, Math.trunc(rawPage)) : 0 }
}

export function listQuery(familia: RegistroFamilia, page: number, finalidade: FinalidadeAcessoArquivo | '', filtros: RegistroFiltros, selection?: RegistroSelecao) {
  const query = new URLSearchParams(`tipo=${familia}&page=${page}${finalidade ? `&finalidade=${finalidade}` : ''}${filtrosRegistroQuery(filtros)}`)
  if (selection) {
    query.set('escopo', selection.escopo)
    selection.ids.forEach((id) => query.append('id', id))
    if (selection.usuarioId) query.set('selecionador', selection.usuarioId)
    if (selection.invalida) query.set('selecaoInvalida', '1')
  }
  return query.toString()
}

export function safeReturn(value: string | null) {
  if (!value?.startsWith('/admin/registros')) return '/admin/registros'
  const url = new URL(value, 'https://local.invalid')
  return url.origin === 'https://local.invalid' && url.pathname === '/admin/registros'
    ? url.pathname + url.search : '/admin/registros'
}

export function clearedSelectionReturn(value: string | null) {
  const safe = safeReturn(value), query = new URL(safe, 'https://local.invalid').searchParams
  const { familia, page, finalidade, filtros } = queryState(query)
  return '/admin/registros?' + listQuery(familia, page, finalidade, filtros, emptySelection())
}

export function snapshotText(value: unknown, field: string) {
  if (!value || typeof value !== 'object') return null
  const text = (value as Record<string, unknown>)[field]
  return typeof text === 'string' && text.trim() ? text : null
}

export function dateTime(value: string | null | undefined, timeZone = 'America/Sao_Paulo') {
  if (!value) return 'Não registrado'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return 'Data indisponível'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'medium', timeZone }).format(date)
}

const calendarFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' })
export function calendarDate(value: string | null | undefined, exclusiveEnd = false) {
  if (!value) return ''
  const instant = new Date(value).getTime() - (exclusiveEnd ? 1 : 0)
  if (!Number.isFinite(instant)) return ''
  const parts = calendarFormatter.formatToParts(new Date(instant))
  return ['year', 'month', 'day'].map((type) => parts.find((part) => part.type === type)?.value).join('-')
}

function civilDay(value: string, days = 0) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('Escolha uma data válida no calendário.')
  const [year, month, day] = value.split('-').map(Number), date = new Date(Date.UTC(year, month - 1, day))
  if (date.toISOString().slice(0, 10) !== value) throw new Error('Escolha uma data válida no calendário.')
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function calendarBoundary(value: string, nextDay = false) {
  const target = civilDay(value, nextDay ? 1 : 0), anchor = Date.parse(target + 'T00:00:00Z')
  // First instant of the civil day, including historical days whose midnight was skipped by DST.
  let lower = anchor - 86_400_000, upper = anchor + 86_400_000
  while (lower < upper) {
    const middle = Math.floor((lower + upper) / 2)
    if (calendarDate(new Date(middle).toISOString()) < target) lower = middle + 1
    else upper = middle
  }
  return new Date(lower).toISOString()
}

export function calendarFilters(filtros: RegistroFiltros, from: string, through: string): RegistroFiltros {
  if (from && through && civilDay(from) > civilDay(through)) throw new Error('A data inicial deve ser anterior ou igual à data final.')
  const result = { ...filtros }
  if (from) result.inicio = calendarBoundary(from); else delete result.inicio
  if (through) result.fim = calendarBoundary(through, true); else delete result.fim
  return result
}

const BENEFITS: Record<string, string> = {
  FOTOS_EXTRA_5: 'Até 10 fotos', ANUNCIO_TOPO: 'Anúncio no topo', STORIES: 'Stories',
  OCULTAR_IDADE: 'Ocultar idade', WHATSAPP_CARD: 'WhatsApp no card', CARROSSEL_FOTOS: 'Carrossel de fotos', VIDEO_1: 'Vídeo no anúncio',
}
export function benefitLabel(value: string | null | undefined) {
  return value ? BENEFITS[value] ?? 'Benefício com código registrado' : 'Benefício não identificado'
}

export function endReasonLabel(fimTipo: string | null | undefined) {
  return fimTipo === 'LIMITE_PREVISTO' ? 'Regra do término previsto'
    : fimTipo === 'ENCERRAMENTO_REGISTRADO' ? 'Motivo do encerramento' : 'Informação sobre o término'
}

const LABELS: Record<string, string> = {
  EM_VEICULACAO: 'Em veiculação', ENCERRADA: 'Encerrada',
  PAGA: 'Paga', ADMINISTRATIVA: 'Administrativa', PROMOCIONAL: 'Promocional',
  ORIGEM_INDETERMINADA: 'Origem indeterminada', ABRANGIDA: 'Abrangida',
  PREVENTIVA: 'Preservação preventiva (enquadramento não confirmado)',
  DESCONHECIDA: 'Desconhecida (não comprovada)',
  ANUNCIO: 'Conteúdo do anúncio', MIDIA_UPLOAD: 'Mídia enviada ao Story',
  ORIGINAL: 'Original', PREVIEW: 'Prévia', MINIATURA: 'Miniatura',
  PREVIEW_RESTRITO: 'Prévia restrita',
  SIM: 'Sim (valor registrado)', NAO: 'Não (valor registrado)',
  REMUNERADA: 'Remuneração registrada (não comprova pagamento)',
  GRATUITA: 'Gratuita (classificação registrada)',
  NAO_ABRANGIDA: 'Não abrangida (classificação registrada)', PENDENTE: 'Pendente',
  LIMITE_PREVISTO: 'Término previsto', ENCERRAMENTO_REGISTRADO: 'Encerramento registrado',
  SEM_TERMINO_REGISTRADO: 'Sem término registrado',
  NAO_AFERIDA_NA_CAPTURA: 'Não aferida na captura', NAO_MENSURADO: 'Não mensurado', DESCONHECIDO: 'Desconhecido (não comprovado)',
  NAO_REGISTRADO: 'Não registrado', NAO_DISPONIVEL_EM_CAMPO_ESTRUTURADO: 'Não disponível em campo estruturado',
  SALDO_FUNGIVEL_SEM_ALOCACAO_EXATA: 'Saldo fungível, sem alocação exata a pagamento',
}

export function captureLabel(value: string) { return LABELS[value] ?? value }

export function label(value: string | null | undefined) {
  if (!value) return 'Não registrado'
  return LABELS[value] ?? value.replaceAll('_', ' ').toLowerCase()
}

export function errorStatus(error: unknown) {
  return error instanceof ApiContractError ? error.status : null
}
