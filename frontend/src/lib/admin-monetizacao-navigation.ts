export const ADMIN_MONETIZACAO_ABAS = [
  'stories',
  'beneficios',
  'pacotes',
  'saldos',
  'ativacoes',
] as const

export type AdminMonetizacaoAba = (typeof ADMIN_MONETIZACAO_ABAS)[number]

type SearchParamsLeitura = Pick<URLSearchParams, 'getAll'>

const UUID_CANONICO = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function adminMonetizacaoAba(params: SearchParamsLeitura): AdminMonetizacaoAba {
  const valores = params.getAll('aba')
  const valor = valores.length === 1 ? valores[0] : null
  return ADMIN_MONETIZACAO_ABAS.includes(valor as AdminMonetizacaoAba)
    ? valor as AdminMonetizacaoAba
    : 'beneficios'
}

export function adminMonetizacaoQuery(
  params: SearchParamsLeitura,
  aba: AdminMonetizacaoAba,
) {
  const next = new URLSearchParams({ aba })
  const anuncioIds = params.getAll('anuncioId')
  const anuncioId = anuncioIds.length === 1 ? anuncioIds[0] : null
  if (anuncioId && UUID_CANONICO.test(anuncioId)) {
    next.set('anuncioId', anuncioId)
  }
  return next.toString()
}

export function anuncioIdLegadoSeguro(value: string | string[] | undefined) {
  return typeof value === 'string' && UUID_CANONICO.test(value) ? value : null
}