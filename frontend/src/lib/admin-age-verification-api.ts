import { adminApiUrl, apiErrorFromResponse } from '@/lib/api-contract'

export type AdminAgeVerification = {
  id: string
  resultado: 'PERMITIDO' | 'NEGADO' | 'INDETERMINADO'
  metodo: 'DECLARACAO' | 'DOCUMENTO' | 'STAFF' | 'IMPORTACAO'
  requestId: string
  criadoEm: string
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
