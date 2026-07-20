import { adminApiUrl, apiErrorFromResponse, requireArrayPayload } from '@/lib/api-contract'
import { corrigirEstruturaTexto } from '@/lib/text/encoding'

export type AdminPagamentoItem = {
  id: string
  usuarioId: string
  provedorDeclarado: string
  provedorClassificado: string
  metodo: string
  statusInterno: string
  statusOperacional: string
  quantidadeCreditos: number | null
  moeda: string
  evidenciaTransacaoPresente: boolean
  evidenciaTransacaoMascarada: string | null
  evidenciaProvedorPresente: boolean
  creditoVinculado: boolean
  criadoEm: string
  atualizadoEm: string
  somenteLeitura: true
}

export type AdminPagamentosPage = {
  itens: AdminPagamentoItem[]
  total: number
  pagina: number
  tamanho: number
  somenteLeitura: true
}

export async function listarAdminPagamentos(pagina = 0, tamanho = 20): Promise<AdminPagamentosPage> {
  const query = new URLSearchParams({ page: String(pagina), size: String(tamanho) })
  const response = await fetch(adminApiUrl(`/pagamentos?${query.toString()}`), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw await apiErrorFromResponse(response)

  const payload = corrigirEstruturaTexto(await response.json()) as Partial<AdminPagamentosPage>
  return {
    ...payload,
    itens: requireArrayPayload<AdminPagamentoItem>(payload.itens),
  } as AdminPagamentosPage
}
