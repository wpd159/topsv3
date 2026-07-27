import { ApiContractError, publicApiUrl } from '@/lib/api-contract'

export type GlobalAgeGateStatus = {
  accepted: boolean
  state: 'GLOBAL_NAO_ACEITO' | 'GLOBAL_ACEITO'
  expiresAt?: string | null
}

export type VisitorAccessStatus = {
  globalAccepted: boolean
  verified: boolean
  level?: string | null
  expiresAt?: string | null
  explicitVerified: boolean
  explicitLevel?: string | null
  explicitExpiresAt?: string | null
  state: string
  riskScore?: number | null
  decision?: string | null
  documentStatus?: string | null
  reasonPublic?: string | null
}

export type VisitorChallenge = {
  challengeId: string
  state: string
  effectiveLevel: string
  scope: string
  expiresAt: string
  requiresExplicitAcknowledgement: boolean
  documentRequired: boolean
  maxAttempts: number
  reasonPublic?: string | null
}

export type VisitorChallengeInput = {
  level: 'LIGHT' | 'REINFORCED' | 'STRONG'
  scope: 'MIDIA_RESTRITA' | 'WHATSAPP' | 'STORY' | 'CONTEUDO_EXPLICITO'
  anuncioId: string
  midiaId?: string
  storyId?: string
  route?: string
  idempotencyKey: string
}

export type VisitorVerifyInput = {
  challengeId: string
  dataNascimento: string
  confirmacaoDataNascimento: string
  cpf: string
  aceiteMaioridade: boolean
  aceiteConteudoRestrito: boolean
  aceitePrivacidade: boolean
  confirmacaoExplicita: boolean
  idempotencyKey: string
}

export type VisitorDocumentStatus = {
  submissionId: string
  challengeId: string
  state: string
  status: string
  createdAt: string
  reasonPublic: string
}

const CSRF_COOKIE = ['XSRF', 'TOKEN'].join('-')
const CSRF_HEADER = ['X', 'XSRF', 'TOKEN'].join('-')

function csrfCookieValue() {
  if (typeof document === 'undefined') return null
  const entry = document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${CSRF_COOKIE}=`))
  return entry ? decodeURIComponent(entry.slice(CSRF_COOKIE.length + 1)) : null
}

async function parseFailure(response: Response): Promise<ApiContractError> {
  let message = ''
  try {
    const body = (await response.json()) as Record<string, unknown>
    for (const key of ['message', 'detail', 'reasonPublic', 'error']) {
      if (typeof body[key] === 'string' && body[key]) {
        message = body[key] as string
        break
      }
    }
  } catch {
    // A resposta sem JSON ainda preserva o status HTTP abaixo.
  }
  const kind = response.status === 403
    ? 'ACCESS_DENIED'
    : response.status === 410
      ? 'CONFLICT'
    : response.status === 409
      ? 'CONFLICT'
      : response.status === 400 || response.status === 422
        ? 'INVALID_REQUEST'
        : 'TECHNICAL_FAILURE'
  return new ApiContractError(
    message || 'Nao foi possivel concluir a verificacao.',
    kind,
    response.status,
    response.status >= 500,
  )
}

async function initializeCsrf() {
  await fetch(publicApiUrl('/compliance/age-gate/status'), {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })
  const value = csrfCookieValue()
  if (!value) {
    throw new ApiContractError(
      'Nao foi possivel inicializar a protecao da solicitacao.',
      'ACCESS_DENIED',
      403,
    )
  }
  return value
}

export async function publicCsrfHeaders(contentType = true) {
  const value = csrfCookieValue() || (await initializeCsrf())
  const headers = new Headers()
  headers.set(CSRF_HEADER, value)
  if (contentType) headers.set('Content-Type', 'application/json')
  return headers
}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(publicApiUrl(path), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw await parseFailure(response)
  return (await response.json()) as T
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const headers = await publicCsrfHeaders()
  const response = await fetch(publicApiUrl(path), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await parseFailure(response)
  return (await response.json()) as T
}

export function newIdempotencyKey(prefix: string) {
  const random = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}:${random}`
}

export function getGlobalAgeGateStatus() {
  return get<GlobalAgeGateStatus>('/compliance/age-gate/status')
}

export function acceptGlobalAgeGate(originPath: string) {
  return post<GlobalAgeGateStatus>('/compliance/age-gate/accept', { originPath })
}

export function createVisitorChallenge(input: VisitorChallengeInput) {
  return post<VisitorChallenge>('/compliance/visitor/challenge', input)
}

export function verifyVisitor(input: VisitorVerifyInput) {
  return post<VisitorAccessStatus>('/compliance/visitor/verify', input)
}

export function getVisitorStatus() {
  return get<VisitorAccessStatus>('/compliance/visitor/status')
}

export async function submitVisitorDocument(
  challengeId: string,
  file: File,
  idempotencyKey: string,
) {
  const headers = await publicCsrfHeaders(false)
  headers.set('Idempotency-Key', idempotencyKey)
  const form = new FormData()
  form.append('document', file)
  const response = await fetch(
    publicApiUrl(`/compliance/visitor/document?challengeId=${encodeURIComponent(challengeId)}`),
    {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store',
      headers,
      body: form,
    },
  )
  if (!response.ok) throw await parseFailure(response)
  return (await response.json()) as VisitorDocumentStatus
}

export function revokeVisitorAccess() {
  return post<VisitorAccessStatus>('/compliance/visitor/revoke', {
    reason: 'REVOGACAO_VISITANTE',
  })
}
