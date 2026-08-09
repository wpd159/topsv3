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

export class PixApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly requestId: string | null
  ) {
    super(message)
    this.name = 'PixApiError'
  }
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
    let bodyRequestId: string | null = null
    try {
      const body = JSON.parse(raw) as { requestId?: unknown }
      if (typeof body.requestId === 'string' && body.requestId.trim()) {
        bodyRequestId = body.requestId.trim().slice(0, 120)
      }
    } catch {
      // The sanitized response can be plain text.
    }
    const headerRequestId = response.headers.get('X-Request-Id')?.trim().slice(0, 120) || null
    throw new PixApiError(
      messageFromApiBody(raw, response.status, fallback),
      response.status,
      headerRequestId || bodyRequestId
    )
  }
  if (!raw.trim()) throw new Error('Resposta vazia do servidor.')
  try {
    return JSON.parse(raw) as T
  } catch {
    throw new Error('Resposta inválida do servidor.')
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
    'Não foi possível carregar o histórico Pix.'
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
  return readJson<CobrancaPix>(response, 'Não foi possível criar a cobrança Pix.')
}

export async function consultarCobrancaPix(pagamentoId: string) {
  const response = await fetch(
    publicApiUrl(`/minha-conta/pagamentos/${encodeURIComponent(pagamentoId)}`),
    {
      credentials: 'include',
      cache: 'no-store',
    }
  )
  return readJson<CobrancaPix>(response, 'Não foi possível consultar a cobrança Pix.')
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
  return readJson<CobrancaPix>(response, 'Não foi possível atualizar o pagamento Pix.')
}

export function novaIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `pix-${crypto.randomUUID()}`
  }
  return `pix-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
