import {
  BackendContractPendingError,
  PENDING_BACKEND_CONTRACTS,
  adminApiUrl,
  apiErrorFromResponse,
  requireArrayPayload,
} from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import type {
  AdminAuditLogItem,
  AdminKycDecision,
  AdminKycSubmission,
  ModerationMediaItem,
  ModerationAnuncioDetail,
  ModerationRevisionDetail,
  ModerationRevisionQueueItem,
  ModerationStaffListItem,
  VisitorVerificationAuditItem,
} from './types'

type ResourceId = string | number

type AdminPage<T> = {
  itens: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

type CanonicalLocation = { uf?: string | null; cidade?: string | null; bairro?: string | null }

type CanonicalAdminAd = {
  id: string
  slug: string
  titulo: string
  descricaoResumo?: string | null
  status: string
  statusModeracao: string
  categoria?: string | null
  localizacao?: CanonicalLocation | null
  criadoEm: string
  atualizadoEm?: string | null
  publicadoEm?: string | null
  ultimaPublicacaoEm?: string | null
  midiasTotal?: number | null
  revisoesTotal?: number | null
  contatoConfigurado?: boolean
  documentoPendente?: boolean
  precoInformado?: boolean
  comercialLimitado?: boolean
}

type CanonicalRevision = {
  id: string
  anuncioId: string
  slugAnuncio: string
  tipo: string
  status: string
  conteudoSolicitadoPresente: boolean
  criadoEm: string
  finalizadoEm?: string | null
  somenteLeitura?: boolean
}

function requestId() {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `admin-${Date.now()}`
}

function csrfCookieName() {
  return ['XSRF', 'TOKEN'].join('-')
}

function csrfHeaderName() {
  return ['X', 'XSRF', 'TOKEN'].join('-')
}

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const name = csrfCookieName()
  const entry = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function adminWriteHeaders(extra?: Record<string, string>) {
  let value = readCsrfValue()
  if (!value) {
    await fetch(adminApiUrl('/auth/me'), { credentials: 'include', cache: 'no-store' })
    value = readCsrfValue()
  }
  return {
    'Content-Type': 'application/json',
    ...(value ? { [csrfHeaderName()]: value } : {}),
    ...extra,
  }
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw await apiErrorFromResponse(response)
  return corrigirEstruturaTexto(await response.json()) as T
}

function requirePage<T>(payload: unknown): AdminPage<T> {
  if (!payload || typeof payload !== 'object') throw new Error('Resposta administrativa incompativel.')
  const page = payload as Partial<AdminPage<T>>
  return { ...page, itens: requireArrayPayload<T>(page.itens) } as AdminPage<T>
}

function isOpenRevision(status: string) {
  return ['PENDENTE', 'EM_ANALISE', 'EM_REVISAO'].includes((status || '').toUpperCase())
}

function mapAdList(item: CanonicalAdminAd): ModerationStaffListItem {
  return {
    id: item.id,
    titulo: item.titulo,
    usernameAnunciante: '',
    status: item.status,
    dataCriacao: item.criadoEm,
    pendingRevision: isOpenRevision(item.statusModeracao),
    pendingRevisionStatus: item.statusModeracao,
    removidoLogicamente: false,
    cidadeNome: item.localizacao?.cidade ?? null,
  }
}

function mapAdDetail(item: CanonicalAdminAd): ModerationAnuncioDetail {
  return {
    id: item.id,
    titulo: item.titulo,
    descricao: item.descricaoResumo ?? null,
    categoria: item.categoria ?? null,
    status: item.status,
    slug: item.slug,
    estadoUf: item.localizacao?.uf ?? null,
    cidadeNome: item.localizacao?.cidade ?? null,
    bairroNome: item.localizacao?.bairro ?? null,
    localizacaoLabel: [item.localizacao?.bairro, item.localizacao?.cidade, item.localizacao?.uf]
      .filter(Boolean)
      .join(' - '),
    pendingRevision: isOpenRevision(item.statusModeracao),
    pendingRevisionStatus: item.statusModeracao,
    dataCriacao: item.criadoEm,
    dataCriacaoIso: item.criadoEm,
  }
}

function mapRevisionList(item: CanonicalRevision): ModerationRevisionQueueItem {
  return {
    revisionId: item.id,
    anuncioId: item.anuncioId,
    anuncioTitulo: item.slugAnuncio,
    status: item.status,
    source: item.tipo,
    submittedAt: item.criadoEm,
  }
}

export async function fetchStaffAnunciosList(): Promise<ModerationStaffListItem[]> {
  const response = await fetch(adminApiUrl('/anuncios?page=0&size=100'), {
    credentials: 'include',
    cache: 'no-store',
  })
  const page = requirePage<CanonicalAdminAd>(await readJson(response))
  return page.itens.map(mapAdList)
}

export async function fetchStaffRevisions(): Promise<ModerationRevisionQueueItem[]> {
  const response = await fetch(adminApiUrl('/moderacao/revisoes?page=0&size=100'), {
    credentials: 'include',
    cache: 'no-store',
  })
  const page = requirePage<CanonicalRevision>(await readJson(response))
  return page.itens.filter((item) => isOpenRevision(item.status)).map(mapRevisionList)
}

export async function fetchStaffAnuncioDetail(id: ResourceId): Promise<ModerationAnuncioDetail> {
  const response = await fetch(adminApiUrl(`/anuncios/${encodeURIComponent(String(id))}`), {
    credentials: 'include',
    cache: 'no-store',
  })
  return mapAdDetail(await readJson<CanonicalAdminAd>(response))
}

export async function fetchStaffRevision(id: ResourceId): Promise<ModerationRevisionDetail | null> {
  const query = new URLSearchParams({ page: '0', size: '20', anuncioId: String(id) })
  const listResponse = await fetch(adminApiUrl(`/moderacao/revisoes?${query.toString()}`), {
    credentials: 'include',
    cache: 'no-store',
  })
  const page = requirePage<CanonicalRevision>(await readJson(listResponse))
  const selected = page.itens.find((item) => isOpenRevision(item.status))
  if (!selected) return null
  const detailResponse = await fetch(
    adminApiUrl(`/moderacao/revisoes/${encodeURIComponent(selected.id)}`),
    { credentials: 'include', cache: 'no-store' }
  )
  const detail = await readJson<CanonicalRevision>(detailResponse)
  return {
    revisionId: detail.id,
    status: detail.status,
    source: detail.tipo,
    submittedAt: detail.criadoEm,
  }
}

export function notifyModerationDataUpdated() {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('admin-revisions-updated'))
}

async function decideRevision(id: ResourceId, decisao: 'APROVAR' | 'REPROVAR', motivo: string) {
  const current = await fetchStaffRevision(id)
  if (!current) {
    const remeterResponse = await fetch(adminApiUrl(`/anuncios/${encodeURIComponent(String(id))}/remeter-revisao`), {
      method: 'POST',
      credentials: 'include',
      headers: await adminWriteHeaders(),
      body: JSON.stringify({ motivo, requestIdCliente: requestId() }),
    })
    await readJson(remeterResponse)
  }
  const revision = current ?? (await fetchStaffRevision(id))
  if (!revision) throw new Error('Nao foi possivel localizar a revisao do anuncio.')
  const response = await fetch(
    adminApiUrl(`/moderacao/revisoes/${encodeURIComponent(String(revision.revisionId))}/decidir`),
    {
      method: 'POST',
      credentials: 'include',
      headers: await adminWriteHeaders(),
      body: JSON.stringify({ decisao, motivo, requestIdCliente: requestId() }),
    }
  )
  return readJson<Record<string, unknown>>(response)
}

export function approveAnuncioApi(id: ResourceId, reason: string) {
  return decideRevision(id, 'APROVAR', reason)
}

export function rejectAnuncioApi(id: ResourceId, justificativa: string) {
  return decideRevision(id, 'REPROVAR', justificativa)
}

export async function alterarStatusStaffApi(
  _id: ResourceId,
  _status: string,
  _motivo?: string
): Promise<Record<string, unknown>> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.moderationLegacyActions)
}

export async function decidirMidiaApi(
  id: ResourceId,
  decisao: 'APROVAR' | 'REPROVAR' | 'SOLICITAR_AJUSTE',
  visibilidadeMidia?: 'LIVRE' | 'RESTRITA_18',
  motivo?: string
): Promise<Record<string, unknown>> {
  const response = await fetch(adminApiUrl(`/midias/${encodeURIComponent(String(id))}/decidir`), {
    method: 'POST',
    credentials: 'include',
    headers: await adminWriteHeaders(),
    body: JSON.stringify({
      decisao,
      visibilidadeMidia,
      motivo: motivo?.trim() || undefined,
      requestIdCliente: requestId(),
    }),
  })
  return readJson(response)
}

export async function fetchAdminKycQueue(): Promise<AdminKycSubmission[]> {
  const response = await fetch(adminApiUrl('/documentos'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return requireArrayPayload<AdminKycSubmission>(await readJson(response))
}

export async function fetchAdminKycTemporaryUrl(documentId: string) {
  const response = await fetch(adminApiUrl(`/documentos/${encodeURIComponent(documentId)}/url-temporaria`), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readJson<{ url: string; expiraEm: string }>(response)
}

export async function decideAdminKyc(submissionId: string, decisao: AdminKycDecision, motivo?: string) {
  const response = await fetch(adminApiUrl(`/documentos/envios/${encodeURIComponent(submissionId)}/decidir`), {
    method: 'POST',
    credentials: 'include',
    headers: await adminWriteHeaders(),
    body: JSON.stringify({ decisao, motivo: motivo?.trim() || null }),
  })
  return readJson<{ envioId: string; status: string; requestId: string; revisadoEm: string }>(response)
}

export async function fetchAdminMidiasV3(): Promise<ModerationMediaItem[]> {
  const response = await fetch(adminApiUrl('/midias?page=0&size=100'), {
    credentials: 'include',
    cache: 'no-store',
  })
  const page = requirePage<ModerationMediaItem>(await readJson(response))
  return page.itens
}

export async function fetchComplianceAuditForAnuncio(_id: ResourceId): Promise<AdminAuditLogItem[]> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.complianceAdmin)
}

export async function fetchVisitorVerificationEventsForAnuncio(
  _id: ResourceId
): Promise<VisitorVerificationAuditItem[]> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.complianceAdmin)
}

export async function removerAnuncioLogicamenteStaffApi(
  _id: ResourceId,
  _motivo: string
): Promise<void> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.moderationLegacyActions)
}

export async function fetchPremiumAnuncioDetailAdmin(anuncioId: ResourceId): Promise<Record<string, unknown>> {
  const encodedId = encodeURIComponent(String(anuncioId))
  const [statusResponse, benefitsResponse] = await Promise.all([
    fetch(adminApiUrl(`/premium/anuncios/${encodedId}`), { credentials: 'include', cache: 'no-store' }),
    fetch(adminApiUrl(`/premium/anuncios/${encodedId}/beneficios`), { credentials: 'include', cache: 'no-store' }),
  ])
  const status = await readJson<Record<string, unknown>>(statusResponse)
  const benefits = requireArrayPayload<Record<string, unknown>>(await readJson(benefitsResponse))
  const active = benefits.filter((item) => ['ATIVO', 'AGENDADO'].includes(String(item.statusCalculado)))
  const expired = benefits.filter((item) => String(item.statusCalculado) === 'EXPIRADO')
  return { ...status, beneficiosAtivos: active, beneficiosExpirados: expired, historico: [] }
}

export async function removerFotosStaffApi(_id: ResourceId, _urls: string[]): Promise<void> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.moderationLegacyActions)
}

export async function removerMidiaRevisaoStaffApi(
  _id: ResourceId,
  _mediaId: ResourceId
): Promise<Record<string, unknown>> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.moderationLegacyActions)
}

export async function ativarPremiumBeneficioAdmin(
  _anuncioId: ResourceId,
  _body: { codigo: string; observacaoInterna?: string }
): Promise<Record<string, unknown>> {
  throw new BackendContractPendingError(PENDING_BACKEND_CONTRACTS.premiumLegacyDashboard)
}

export async function desativarPremiumBeneficioAdmin(
  ativacaoId: ResourceId,
  body?: { motivo?: string; observacaoInterna?: string }
): Promise<Record<string, unknown>> {
  const response = await fetch(adminApiUrl(`/premium/ativacoes/${encodeURIComponent(String(ativacaoId))}/cancelar`), {
    method: 'POST',
    credentials: 'include',
    headers: await adminWriteHeaders({ 'Idempotency-Key': requestId() }),
    body: JSON.stringify({ motivo: body?.motivo || 'Cancelamento administrativo' }),
  })
  return readJson(response)
}
