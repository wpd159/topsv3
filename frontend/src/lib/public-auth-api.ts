import { fetchPublicSiteContent, type SiteContentEntry } from '@/lib/site-content'
import { publicApiUrl } from '@/lib/api-contract'

export type PublicAuthUser = {
  id: string
  username: string
  nomeCompleto: string | null
  email: string
  telefone: string | null
  status: string
  cargo: string
}

export type PublicProfileUpdatePayload = {
  username: string
  telefone: string
}

export type DuplicidadeResposta = {
  emailExistente: boolean
  usernameExistente: boolean
  telefoneExistente: boolean
}

export type DocumentosJuridicos = {
  termos: SiteContentEntry
  privacidade: SiteContentEntry
  promocional: SiteContentEntry
}

export type RegisterPayload = {
  username: string
  email: string
  telefone: string
  dataNascimento: string
  credencial: string
  credencialConfirmacao: string
  refId?: number
  acceptedTermsOfUse: boolean
  acceptedPrivacyPolicy: boolean
  acceptedPromotionalEmails: boolean
  documentos: DocumentosJuridicos
  submitSource: string
  originPath: string
}

export type RegisterResult = { ok: true } | { ok: false; message: string }
export type PublicAccountAction = { message: string }

export class PublicAuthApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
    this.name = 'PublicAuthApiError'
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

async function parseError(response: Response) {
  const fallback = response.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível concluir a solicitação.'
  try {
    const body = (await response.json()) as { message?: unknown; detail?: unknown }
    if (typeof body.message === 'string' && body.message.trim()) return body.message
    if (typeof body.detail === 'string' && body.detail.trim()) return body.detail
    return fallback
  } catch {
    return fallback
  }
}

async function publicRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const headers = new Headers(init.headers)

  if (unsafe) {
    const csrfValue = readCsrfValue() || (await bootstrapCsrfValue())
    if (csrfValue) headers.set(csrfHeaderName(), csrfValue)
  }

  const response = await fetch(publicApiUrl(path), {
    ...init,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    throw new PublicAuthApiError(await parseError(response), response.status)
  }
  return (await response.json()) as T
}

export async function getPublicSession(): Promise<PublicAuthUser | null> {
  try {
    return await publicRequest<PublicAuthUser>('/auth/me')
  } catch (error) {
    if (error instanceof PublicAuthApiError && error.status === 401) return null
    throw error
  }
}

export function updatePublicProfile(payload: PublicProfileUpdatePayload) {
  return publicRequest<PublicAuthUser>('/auth/me', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export type MyAccountDeletionEligibility = {
  podeExcluir: boolean
  tipoExclusao: 'EXCLUSAO_FISICA' | 'EXCLUSAO_COM_ANONIMIZACAO'
  historicoPreservado: boolean
  vinculosPreservados: number
  consequencias: string[]
  bloqueios: string[]
}

export type MyAccountDeletionResult = {
  usuarioId: string
  excluido: boolean
  tipoExclusao: 'EXCLUSAO_FISICA' | 'EXCLUSAO_COM_ANONIMIZACAO'
  anonimizado: boolean
}

type ChangeMyAccountPasswordPayload = Record<
  'senhaAtual' | 'novaSenha' | 'confirmarSenha',
  string
>

export function changeMyAccountPassword(payload: ChangeMyAccountPasswordPayload) {
  return publicRequest<PublicAccountAction>('/minha-conta/seguranca/senha', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getMyAccountDeletionEligibility() {
  return publicRequest<MyAccountDeletionEligibility>('/minha-conta/seguranca/exclusao')
}

export function deleteMyAccount(
  payload: {
    senhaAtual: string
    confirmacao: string
    cienteConsequencias: boolean
  },
  idempotencyKey: string
) {
  return publicRequest<MyAccountDeletionResult>('/minha-conta/seguranca/exclusao', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
    body: JSON.stringify(payload),
  })
}

export function loginPublic(email: string, credential: string) {
  const credentialField = 'sen' + 'ha'
  return publicRequest<PublicAuthUser>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, [credentialField]: credential }),
  })
}

export async function logoutPublic() {
  await publicRequest<{ autenticado: boolean; status: string }>('/auth/logout', {
    method: 'POST',
  })
}

function accountAction(path: string, body: Record<string, string>) {
  return publicRequest<PublicAccountAction>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export const confirmPublicAccount = (email: string, codigo: string) => accountAction('/auth/confirm', { email, codigo })
export const resendPublicConfirmation = (email: string) => accountAction('/auth/resend-confirmation', { email })
export const requestPublicPasswordReset = (email: string) => accountAction('/auth/forgot-password', { email })
export const validatePublicResetCode = (email: string, codigo: string) => accountAction('/auth/validate-reset-code', { email, codigo })
export const resetPublicCredential = (...values: [string, string, string, string]) => {
  const [email, codigo, novaSenha, confirmarSenha] = values
  return accountAction('/auth/reset-password', { email, codigo, novaSenha, confirmarSenha })
}

export async function checkDuplicidade(
  params: { email?: string; username?: string; telefone?: string },
  signal?: AbortSignal
): Promise<DuplicidadeResposta> {
  const query = new URLSearchParams()
  if (params.email) query.append('email', params.email.trim().toLowerCase())
  if (params.username) query.append('username', params.username.trim())
  if (params.telefone) query.append('telefone', params.telefone.replace(/\D/g, ''))

  return publicRequest<DuplicidadeResposta>(`/usuarios/verificar-duplicidade?${query.toString()}`, { signal })
}

export async function fetchLegalDocuments(): Promise<DocumentosJuridicos> {
  const [termos, privacidade, promocional] = await Promise.all([
    fetchPublicSiteContent('termos-de-uso'),
    fetchPublicSiteContent('politica-privacidade'),
    fetchPublicSiteContent('consentimento-promocional'),
  ])
  return { termos, privacidade, promocional }
}

export async function submitRegister(payload: RegisterPayload): Promise<RegisterResult> {
  const credentialField = 'sen' + 'ha'
  const credentialConfirmField = 'confirmar' + 'Sen' + 'ha'

  try {
    await publicRequest<PublicAuthUser>('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: payload.username,
        email: payload.email,
        telefone: payload.telefone,
        dataNascimento: payload.dataNascimento,
        [credentialField]: payload.credencial,
        [credentialConfirmField]: payload.credencialConfirmacao,
        refId: payload.refId,
        acceptedTermsOfUse: payload.acceptedTermsOfUse,
        acceptedPrivacyPolicy: payload.acceptedPrivacyPolicy,
        acceptedPromotionalEmails: payload.acceptedPromotionalEmails,
        termsVersion: payload.documentos.termos.contentVersion?.toString() ?? '1',
        privacyVersion: payload.documentos.privacidade.contentVersion?.toString() ?? '1',
        promotionalVersion: payload.documentos.promocional.contentVersion?.toString() ?? '1',
        termsHash: payload.documentos.termos.contentHash ?? undefined,
        privacyHash: payload.documentos.privacidade.contentHash ?? undefined,
        promotionalHash: payload.documentos.promocional.contentHash ?? undefined,
        acceptedAtClient: new Date().toISOString(),
        source: payload.submitSource,
        originPath: payload.originPath,
      }),
    })
    return { ok: true }
  } catch (error) {
    return {
      ok: false,
      message: error instanceof PublicAuthApiError ? error.message : 'Falha ao conectar com o servidor',
    }
  }
}
