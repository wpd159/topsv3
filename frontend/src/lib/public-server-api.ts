import 'server-only'

import { randomUUID } from 'node:crypto'
import { unstable_cache } from 'next/cache'
import { ApiContractError, type ApiFailureKind } from '@/lib/api-contract'

const DEFAULT_PUBLIC_API_TIMEOUT_MS = 5_000
const MIN_PUBLIC_API_TIMEOUT_MS = 100
const MAX_PUBLIC_API_TIMEOUT_MS = 30_000
const INTERNAL_API_PATH = '/api/public'

export type PublicServerApiFailureReason =
  | 'NOT_FOUND'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RATE_LIMITED'
  | 'UPSTREAM_FAILURE'
  | 'HTTP_FAILURE'
  | 'TIMEOUT'
  | 'NETWORK_FAILURE'
  | 'INVALID_JSON'
  | 'INVALID_CONTRACT'
  | 'CALLER_ABORTED'
  | 'CONFIGURATION'

export class PublicServerApiError extends ApiContractError {
  readonly reason: PublicServerApiFailureReason
  readonly endpointFamily: string
  readonly durationMs: number
  readonly timeoutMs: number

  constructor(options: {
    message: string
    kind: ApiFailureKind
    status: number | null
    retryable: boolean
    requestId: string | null
    reason: PublicServerApiFailureReason
    endpointFamily: string
    durationMs: number
    timeoutMs: number
  }) {
    super(
      options.message,
      options.kind,
      options.status,
      options.retryable,
      options.requestId,
    )
    this.name = 'PublicServerApiError'
    this.reason = options.reason
    this.endpointFamily = options.endpointFamily
    this.durationMs = options.durationMs
    this.timeoutMs = options.timeoutMs
  }
}

export type PublicServerCachePolicy =
  | { mode: 'no-store' }
  | { mode: 'revalidate'; seconds: number; tags: readonly string[] }

type Environment = Record<string, string | undefined>

export type PublicServerJsonOptions<T> = {
  endpointFamily: string
  cache: PublicServerCachePolicy
  validate: (payload: unknown) => T
  signal?: AbortSignal
  timeoutMs?: number
}

function configurationError(message: string, endpointFamily = 'configuration') {
  return new PublicServerApiError({
    message,
    kind: 'TECHNICAL_FAILURE',
    status: null,
    retryable: false,
    requestId: null,
    reason: 'CONFIGURATION',
    endpointFamily,
    durationMs: 0,
    timeoutMs: DEFAULT_PUBLIC_API_TIMEOUT_MS,
  })
}

function normalizeApiBase(value: string, variableName: string) {
  let parsed: URL
  try {
    parsed = new URL(value)
  } catch {
    throw configurationError(`${variableName} deve ser uma URL HTTP absoluta valida.`)
  }

  const normalizedPath = parsed.pathname.replace(/\/+$/, '')
  if (
    !['http:', 'https:'].includes(parsed.protocol) ||
    parsed.username ||
    parsed.password ||
    parsed.search ||
    parsed.hash ||
    normalizedPath !== INTERNAL_API_PATH
  ) {
    throw configurationError(
      `${variableName} deve apontar para o contexto publico da API, sem credenciais, query ou fragmento.`,
    )
  }

  parsed.pathname = normalizedPath
  return parsed.toString().replace(/\/$/, '')
}

export function resolveInternalPublicApiBase(environment: Environment = process.env) {
  const configured = environment.INTERNAL_API_URL?.trim()
  if (configured) {
    const internalBase = normalizeApiBase(configured, 'INTERNAL_API_URL')
    const publicConfigured = environment.NEXT_PUBLIC_API_URL?.trim()
    if (publicConfigured && /^https?:\/\//i.test(publicConfigured)) {
      const publicBase = normalizeApiBase(publicConfigured, 'NEXT_PUBLIC_API_URL')
      if (environment.NODE_ENV === 'production' && internalBase === publicBase) {
        throw configurationError(
          'INTERNAL_API_URL deve usar a rota privada da infraestrutura, nao o endpoint publico.',
        )
      }
    }
    return internalBase
  }

  if (environment.NODE_ENV === 'production') {
    throw configurationError('INTERNAL_API_URL e obrigatoria no ambiente de producao.')
  }

  const developmentFallback = environment.NEXT_PUBLIC_API_URL?.trim()
  if (!developmentFallback || !/^https?:\/\//i.test(developmentFallback)) {
    throw configurationError(
      'Defina INTERNAL_API_URL ou uma NEXT_PUBLIC_API_URL absoluta em desenvolvimento e testes.',
    )
  }
  return normalizeApiBase(developmentFallback, 'NEXT_PUBLIC_API_URL')
}

export function resolvePublicApiTimeoutMs(environment: Environment = process.env) {
  const configured = environment.PUBLIC_API_TIMEOUT_MS?.trim()
  if (!configured) return DEFAULT_PUBLIC_API_TIMEOUT_MS
  if (!/^\d+$/.test(configured)) {
    throw configurationError('PUBLIC_API_TIMEOUT_MS deve ser um inteiro em milissegundos.')
  }
  const timeoutMs = Number(configured)
  if (
    !Number.isSafeInteger(timeoutMs) ||
    timeoutMs < MIN_PUBLIC_API_TIMEOUT_MS ||
    timeoutMs > MAX_PUBLIC_API_TIMEOUT_MS
  ) {
    throw configurationError(
      `PUBLIC_API_TIMEOUT_MS deve estar entre ${MIN_PUBLIC_API_TIMEOUT_MS} e ${MAX_PUBLIC_API_TIMEOUT_MS}.`,
    )
  }
  return timeoutMs
}

const productionInternalApiBase = process.env.NODE_ENV === 'production'
  ? resolveInternalPublicApiBase()
  : null

function normalizedPublicPath(path: string) {
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw configurationError('O caminho publico server-side deve iniciar com uma unica barra.')
  }
  if (path === INTERNAL_API_PATH || path.startsWith(`${INTERNAL_API_PATH}/`)) {
    throw configurationError('O caminho publico server-side nao deve repetir /api/public.')
  }
  return path
}

function validateEndpointFamily(endpointFamily: string) {
  if (!/^[a-z0-9][a-z0-9.-]*$/.test(endpointFamily)) {
    throw configurationError('A familia do endpoint deve usar somente identificadores sanitizados.')
  }
}

function validateCachePolicy(path: string, policy: PublicServerCachePolicy) {
  if (policy.mode === 'no-store') return
  if (!Number.isInteger(policy.seconds) || policy.seconds <= 0) {
    throw configurationError('O TTL do cache publico deve ser um inteiro positivo.')
  }
  if (policy.tags.length === 0 || policy.tags.some((tag) => !/^[a-z0-9][a-z0-9:-]*$/.test(tag))) {
    throw configurationError('O cache publico estavel exige tags sanitizadas.')
  }

  const parsed = new URL(`https://internal.invalid${path}`)
  const sensitivePath = /\/(auth|idade|compliance|minha-conta|pagamentos|creditos|documentos|kyc|webhooks|midias?\/(url-assinada|signed-url|presigned))(\/|$)/
  if (parsed.searchParams.has('ordemSeed') || sensitivePath.test(parsed.pathname)) {
    throw configurationError('Dados aleatorios, autenticados ou sensiveis nao podem usar cache compartilhado.')
  }
}

function durationSince(startedAt: number) {
  return Math.max(0, Math.round(performance.now() - startedAt))
}

function errorForStatus(
  status: number,
  requestId: string | null,
  endpointFamily: string,
  durationMs: number,
  timeoutMs: number,
) {
  const base = { status, requestId, endpointFamily, durationMs, timeoutMs }
  switch (status) {
    case 401:
      return new PublicServerApiError({
        ...base,
        message: 'A leitura publica nao foi autorizada pelo servico.',
        kind: 'SESSION_REQUIRED',
        retryable: false,
        reason: 'UNAUTHORIZED',
      })
    case 403:
      return new PublicServerApiError({
        ...base,
        message: 'A leitura publica foi recusada pelo servico.',
        kind: 'ACCESS_DENIED',
        retryable: false,
        reason: 'FORBIDDEN',
      })
    case 404:
      return new PublicServerApiError({
        ...base,
        message: 'O recurso publico solicitado nao foi encontrado.',
        kind: 'INTEGRATION_MISSING',
        retryable: false,
        reason: 'NOT_FOUND',
      })
    case 429:
      return new PublicServerApiError({
        ...base,
        message: 'O servico publico limitou temporariamente as consultas.',
        kind: 'TECHNICAL_FAILURE',
        retryable: true,
        reason: 'RATE_LIMITED',
      })
    default:
      return new PublicServerApiError({
        ...base,
        message: status >= 500
          ? 'O servico publico esta temporariamente indisponivel.'
          : 'O servico publico recusou a consulta.',
        kind: 'TECHNICAL_FAILURE',
        retryable: status >= 500,
        reason: status >= 500 ? 'UPSTREAM_FAILURE' : 'HTTP_FAILURE',
      })
  }
}

function logSanitizedFailure(error: PublicServerApiError) {
  console.error('public-server-api-failure', {
    requestId: error.requestId,
    endpointFamily: error.endpointFamily,
    method: 'GET',
    status: error.status,
    errorType: error.reason,
    durationMs: error.durationMs,
    timeoutMs: error.timeoutMs,
    buildVersion: process.env.BUILD_VERSION || process.env.GITHUB_SHA || 'unknown',
  })
}

async function executePublicServerJson<T>(
  path: string,
  options: PublicServerJsonOptions<T>,
): Promise<T> {
  const timeoutMs = options.timeoutMs ?? resolvePublicApiTimeoutMs()
  if (
    !Number.isSafeInteger(timeoutMs) ||
    timeoutMs < MIN_PUBLIC_API_TIMEOUT_MS ||
    timeoutMs > MAX_PUBLIC_API_TIMEOUT_MS
  ) {
    throw configurationError('O timeout especifico da leitura publica e invalido.', options.endpointFamily)
  }

  const startedAt = performance.now()
  const localRequestId = randomUUID()
  const controller = new AbortController()
  let timedOut = false
  let callerAborted = options.signal?.aborted === true
  let responseStatus: number | null = null
  let responseRequestId: string | null = localRequestId

  const abortFromCaller = () => {
    callerAborted = true
    controller.abort()
  }
  options.signal?.addEventListener('abort', abortFromCaller, { once: true })
  if (callerAborted) controller.abort()

  const timeout = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)

  try {
    const response = await fetch(`${productionInternalApiBase ?? resolveInternalPublicApiBase()}${path}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        'X-Request-Id': localRequestId,
      },
      cache: 'no-store',
      signal: controller.signal,
    })
    responseStatus = response.status
    responseRequestId = response.headers.get('X-Request-Id') || localRequestId

    if (!response.ok) {
      throw errorForStatus(
        response.status,
        responseRequestId,
        options.endpointFamily,
        durationSince(startedAt),
        timeoutMs,
      )
    }

    let payload: unknown
    try {
      payload = await response.json()
    } catch (error) {
      if (controller.signal.aborted) throw error
      throw new PublicServerApiError({
        message: 'O servico publico retornou JSON invalido.',
        kind: 'TECHNICAL_FAILURE',
        status: response.status,
        retryable: true,
        requestId: responseRequestId,
        reason: 'INVALID_JSON',
        endpointFamily: options.endpointFamily,
        durationMs: durationSince(startedAt),
        timeoutMs,
      })
    }

    try {
      return options.validate(payload)
    } catch (error) {
      if (error instanceof PublicServerApiError) throw error
      throw new PublicServerApiError({
        message: 'O servico publico retornou um contrato incompativel.',
        kind: 'TECHNICAL_FAILURE',
        status: response.status,
        retryable: false,
        requestId: responseRequestId,
        reason: 'INVALID_CONTRACT',
        endpointFamily: options.endpointFamily,
        durationMs: durationSince(startedAt),
        timeoutMs,
      })
    }
  } catch (error) {
    let typedError: PublicServerApiError
    if (error instanceof PublicServerApiError) {
      typedError = error
    } else if (callerAborted) {
      typedError = new PublicServerApiError({
        message: 'A consulta publica foi cancelada pelo chamador.',
        kind: 'NETWORK_FAILURE',
        status: responseStatus,
        retryable: false,
        requestId: responseRequestId,
        reason: 'CALLER_ABORTED',
        endpointFamily: options.endpointFamily,
        durationMs: durationSince(startedAt),
        timeoutMs,
      })
    } else if (timedOut) {
      typedError = new PublicServerApiError({
        message: 'O servico publico excedeu o tempo limite da consulta.',
        kind: 'NETWORK_FAILURE',
        status: responseStatus,
        retryable: true,
        requestId: responseRequestId,
        reason: 'TIMEOUT',
        endpointFamily: options.endpointFamily,
        durationMs: durationSince(startedAt),
        timeoutMs,
      })
    } else {
      typedError = new PublicServerApiError({
        message: 'Nao foi possivel conectar ao servico publico.',
        kind: 'NETWORK_FAILURE',
        status: responseStatus,
        retryable: true,
        requestId: responseRequestId,
        reason: 'NETWORK_FAILURE',
        endpointFamily: options.endpointFamily,
        durationMs: durationSince(startedAt),
        timeoutMs,
      })
    }
    logSanitizedFailure(typedError)
    throw typedError
  } finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', abortFromCaller)
  }
}

export async function publicServerApiJson<T>(
  rawPath: string,
  options: PublicServerJsonOptions<T>,
): Promise<T> {
  const path = normalizedPublicPath(rawPath)
  validateEndpointFamily(options.endpointFamily)
  validateCachePolicy(path, options.cache)

  if (options.cache.mode === 'no-store') {
    return executePublicServerJson(path, options)
  }

  const timeoutMs = options.timeoutMs ?? resolvePublicApiTimeoutMs()
  const loadCached = unstable_cache(
    () => executePublicServerJson(path, { ...options, timeoutMs, signal: undefined }),
    ['public-server-api-v1', options.endpointFamily, path, String(timeoutMs)],
    {
      revalidate: options.cache.seconds,
      tags: [...options.cache.tags],
    },
  )
  return loadCached()
}
