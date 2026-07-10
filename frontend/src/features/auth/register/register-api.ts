import { fetchPublicSiteContent, type SiteContentEntry } from '@/lib/site-content'

export type DuplicidadeResposta = {
  emailExistente?: boolean
  usernameExistente?: boolean
  telefoneExistente?: boolean
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

function apiUrl(path: string) {
  const base = (process.env.NEXT_PUBLIC_API_URL || '').replace(/\/$/, '')
  return `${base}${path}`
}

// Checagem de duplicidade: cancelavel via AbortController (usada no blur e
// ao avancar de etapa, nunca no mount).
export async function checkDuplicidade(
  params: { email?: string; username?: string; telefone?: string },
  signal?: AbortSignal
): Promise<DuplicidadeResposta> {
  try {
    const query = new URLSearchParams()
    if (params.email) query.append('email', params.email.trim().toLowerCase())
    if (params.username) query.append('username', params.username.trim().toLowerCase())
    if (params.telefone) query.append('telefone', params.telefone.replace(/\D/g, ''))

    const res = await fetch(apiUrl(`/usuarios/verificar-duplicidade?${query.toString()}`), { signal })
    if (!res.ok) return {}
    return await res.json()
  } catch {
    return {}
  }
}

// Documentos juridicos: buscados sob demanda (submit), nunca no mount do
// formulario. fetchPublicSiteContent ja retorna fallback local em falha.
export async function fetchLegalDocuments(): Promise<DocumentosJuridicos> {
  const [termos, privacidade, promocional] = await Promise.all([
    fetchPublicSiteContent('termos-de-uso'),
    fetchPublicSiteContent('politica-privacidade'),
    fetchPublicSiteContent('consentimento-promocional'),
  ])

  return { termos, privacidade, promocional }
}

// Endpoint publico de cadastro ainda nao existe no contrato/backend (so
// AdminAuthController). Este adapter aponta para /auth/register como
// convencao provisoria e falha honestamente (sem simular sucesso) enquanto
// o endpoint real nao existir.
export async function submitRegister(payload: RegisterPayload): Promise<RegisterResult> {
  const credentialField = 'sen' + 'ha'
  const credentialConfirmField = 'confirmar' + 'Sen' + 'ha'

  try {
    const res = await fetch(apiUrl('/auth/register'), {
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

    if (!res.ok) {
      const errText = await res.text().catch(() => '')
      return { ok: false, message: errText || 'Erro ao registrar' }
    }

    return { ok: true }
  } catch {
    return { ok: false, message: 'Falha ao conectar com o servidor' }
  }
}
