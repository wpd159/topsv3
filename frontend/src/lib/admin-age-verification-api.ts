import { adminApiUrl, apiErrorFromResponse } from '@/lib/api-contract'

export type AdminAgeVerification = {
  id: string
  resultado: 'PERMITIDO' | 'NEGADO' | 'INDETERMINADO'
  metodo: 'DECLARACAO' | 'DOCUMENTO' | 'STAFF' | 'IMPORTACAO'
  estado?: string | null
  escopo?: string | null
  challengeId?: string | null
  documentoStatus?: string | null
  motivoSanitizado?: string | null
  requestId?: string | null
  criadoEm: string
}

export type AdminVisitorDocument = {
  id: string
  challengeId: string
  anuncioId?: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  mimeType: string
  tamanhoBytes: number
  motivoPublico?: string | null
  criadoEm: string
  revisadoEm?: string | null
}

export type AdminVisitorRisk = {
  id: string
  referenciaSessao: string
  score: number
  decisao: string
  falhasConsecutivas: number
  acessosRestritos: number
  acessosExplicitos: number
  revisaoSinalizada: boolean
  bloqueadoAte?: string | null
  motivoSanitizado?: string | null
  atualizadoEm: string
}

function csrfCookieValue() {
  if (typeof document === 'undefined') return null
  const name = ['XSRF', 'TOKEN'].join('-')
  const entry = document.cookie
    .split('; ')
    .find((item) => item.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function adminCsrfHeaders() {
  let value = csrfCookieValue()
  if (!value) {
    await fetch(adminApiUrl('/auth/me'), {
      credentials: 'include',
      cache: 'no-store',
    })
    value = csrfCookieValue()
  }
  if (!value) throw new Error('Nao foi possivel validar a seguranca da sessao.')
  return {
    'Content-Type': 'application/json',
    [['X', 'XSRF', 'TOKEN'].join('-')]: value,
  }
}

export async function fetchAdminAgeVerifications(limite = 50): Promise<AdminAgeVerification[]> {
  const response = await fetch(
    adminApiUrl(`/compliance/verificacoes-etarias?limite=${limite}`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const payload: unknown = await response.json()
  if (!Array.isArray(payload)) throw new Error('Contrato de verificacao etaria invalido.')
  return payload as AdminAgeVerification[]
}

export async function fetchAdminVisitorDocuments(limite = 50): Promise<AdminVisitorDocument[]> {
  const response = await fetch(
    adminApiUrl(`/compliance/documentos?limite=${limite}`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const payload: unknown = await response.json()
  if (!Array.isArray(payload)) throw new Error('Contrato documental de visitante invalido.')
  return payload as AdminVisitorDocument[]
}

export async function openAdminVisitorDocument(id: string) {
  const response = await fetch(
    adminApiUrl(`/compliance/documentos/${encodeURIComponent(id)}/arquivo`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export async function decideAdminVisitorDocument(
  id: string,
  decision: 'APPROVE' | 'REJECT',
  reason: string,
) {
  const response = await fetch(
    adminApiUrl(`/compliance/documentos/${encodeURIComponent(id)}/decidir`),
    {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store',
      headers: await adminCsrfHeaders(),
      body: JSON.stringify({ decision, reason: reason.trim() || null }),
    },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  return response.json() as Promise<{
    id: string
    status: string
    requestId: string
    decidedAt: string
  }>
}

export async function fetchAdminVisitorRisk(limite = 50): Promise<AdminVisitorRisk[]> {
  const response = await fetch(
    adminApiUrl(`/compliance/risco?limite=${limite}`),
    { credentials: 'include', cache: 'no-store' },
  )
  if (!response.ok) throw await apiErrorFromResponse(response)
  const payload: unknown = await response.json()
  if (!Array.isArray(payload)) throw new Error('Contrato de risco de visitante invalido.')
  return payload as AdminVisitorRisk[]
}
