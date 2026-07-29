'use client'

import { publicApiUrl } from '@/lib/api-contract'
import { messageFromApiBody } from '@/utils/read-api-error-response'

export type PixStatus = 'PENDENTE' | 'APROVADO' | 'EXPIRADO' | 'CANCELADO' | 'FALHO'

export type CobrancaPix = {
  pagamentoId: string
  planoCreditoId: string
  planoNome: string
  identificacaoSanitizada: string
  status: PixStatus
  valor: number
  quantidadeCreditos: number
  criadoEm: string
  expiracaoEm: string | null
  confirmadoEm: string | null
  pixCopiaECola: string | null
  imagemQrCode: string | null
  creditado: boolean
  idempotente: boolean
}

export type PagamentoPixHistorico = {
  pagamentoId: string
  planoCreditoId: string
  planoNome: string
  quantidadeCreditos: number
  valor: number
  criadoEm: string
  expiracaoEm: string | null
  status: PixStatus
  identificacaoSanitizada: string
  confirmadoEm: string | null
}

const csrfCookieName = ['XSRF', 'TOKEN'].join('-')
const csrfHeaderName = ['X', 'XSRF', 'TOKEN'].join('-')

function readCsrfValue() {
  if (typeof document === 'undefined') return null
  const entry = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${csrfCookieName}=`))
  return entry ? decodeURIComponent(entry.slice(csrfCookieName.length + 1)) : null
}

async function csrfValue() {
  const current = readCsrfValue()
  if (current) return current
  await fetch(publicApiUrl('/auth/me'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readCsrfValue()
}

async function readJson<T>(response: Response, fallback: string): Promise<T> {
  const raw = await response.text()
  if (!response.ok) {
    throw new Error(messageFromApiBody(raw, response.status, fallback))
  }
  if (!raw.trim()) throw new Error('Resposta vazia do servidor.')
  try {
    return JSON.parse(raw) as T
  } catch {
    throw new Error('Resposta invalida do servidor.')
  }
}

async function mutationHeaders(idempotencyKey?: string) {
  const headers = new Headers({
    Accept: 'application/json',
    'Content-Type': 'application/json',
  })
  const csrf = await csrfValue()
  if (csrf) headers.set(csrfHeaderName, csrf)
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey)
  return headers
}

export async function listarPagamentosPix() {
  const response = await fetch(publicApiUrl('/minha-conta/pagamentos'), {
    credentials: 'include',
    cache: 'no-store',
  })
  return readJson<PagamentoPixHistorico[]>(
    response,
    'Nao foi possivel carregar o historico Pix.'
  )
}

export async function criarCobrancaPix(planoCreditoId: string, idempotencyKey: string) {
  const response = await fetch(publicApiUrl('/minha-conta/pagamentos/pix'), {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    headers: await mutationHeaders(idempotencyKey),
    body: JSON.stringify({ planoCreditoId }),
  })
  return readJson<CobrancaPix>(response, 'Nao foi possivel criar a cobranca Pix.')
}

export async function consultarCobrancaPix(pagamentoId: string) {
  const response = await fetch(
    publicApiUrl(`/minha-conta/pagamentos/${encodeURIComponent(pagamentoId)}`),
    {
      credentials: 'include',
      cache: 'no-store',
    }
  )
  return readJson<CobrancaPix>(response, 'Nao foi possivel consultar a cobranca Pix.')
}

export async function conciliarCobrancaPix(pagamentoId: string) {
  const response = await fetch(
    publicApiUrl(`/minha-conta/pagamentos/${encodeURIComponent(pagamentoId)}/conciliar`),
    {
      method: 'POST',
      credentials: 'include',
      cache: 'no-store',
      headers: await mutationHeaders(),
      body: '{}',
    }
  )
  return readJson<CobrancaPix>(response, 'Nao foi possivel atualizar o pagamento Pix.')
}

export function novaIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `pix-${crypto.randomUUID()}`
  }
  return `pix-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
