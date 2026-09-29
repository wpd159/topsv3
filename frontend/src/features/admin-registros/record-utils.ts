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

export function listQuery(familia: RegistroFamilia, page: number, finalidade: FinalidadeAcessoArquivo | '', filtros: RegistroFiltros) {
  return `tipo=${familia}&page=${page}${finalidade ? `&finalidade=${finalidade}` : ''}${filtrosRegistroQuery(filtros)}`
}

export function safeReturn(value: string | null) {
  if (!value?.startsWith('/admin/registros')) return '/admin/registros'
  const url = new URL(value, 'https://local.invalid')
  return url.origin === 'https://local.invalid' && url.pathname === '/admin/registros'
    ? url.pathname + url.search : '/admin/registros'
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
}

export function label(value: string | null | undefined) {
  if (!value) return 'Não registrado'
  return LABELS[value] ?? value.replaceAll('_', ' ').toLowerCase()
}

export function errorStatus(error: unknown) {
  return error instanceof ApiContractError ? error.status : null
}
