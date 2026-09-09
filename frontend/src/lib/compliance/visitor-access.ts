"use client"

import {
  acceptGlobalAgeGate,
  getVisitorStatus,
  type VisitorAccessStatus,
} from '@/lib/compliance/age-gate-api'
import type { StatusEscopoVisitante } from '@/lib/compliance/visitor-access-policy'
export {
  statusSatisfazEscopo,
  type EscopoAcessoVisitante,
  type NivelAcessoVisitante,
} from '@/lib/compliance/visitor-access-policy'

export type StatusVisitante = StatusEscopoVisitante & {
  state?: string | null
  riskScore?: number | null
  decision?: string | null
  documentStatus?: string | null
  reasonPublic?: string | null
}

let cacheStatus: StatusVisitante | null = null
let cacheExpiresAt = 0
let pendingRequest: {
  generation: number
  promise: Promise<StatusVisitante>
} | null = null
let statusGeneration = 0

const CACHE_TTL_MS = 30_000
export const AGE_VERIFICATION_CHANGED_EVENT = 'topsv3:age-verification-changed'

// Consumers must not apply a response captured before a newer invalidation.
export function obterGeracaoStatusVisitante(): number {
  return statusGeneration
}

function mapStatus(status: VisitorAccessStatus): StatusVisitante {
  return {
    globalAccepted: status.globalAccepted,
    verified: status.verified,
    level: status.level,
    expiresAt: status.expiresAt,
    explicitVerified: status.explicitVerified,
    explicitLevel: status.explicitLevel,
    explicitExpiresAt: status.explicitExpiresAt,
    state: status.state,
    riskScore: status.riskScore,
    decision: status.decision,
    documentStatus: status.documentStatus,
    reasonPublic: status.reasonPublic,
  }
}

function failClosedStatus(): StatusVisitante {
  return {
    globalAccepted: false,
    verified: false,
  }
}

function authoritativeStatusAfter(
  requestGeneration: number,
): Promise<StatusVisitante> {
  const latestRequest = pendingRequest
  if (latestRequest && latestRequest.generation > requestGeneration) {
    return latestRequest.promise
  }
  return Promise.resolve(cacheStatus ?? failClosedStatus())
}

function refreshStatus() {
  const requestGeneration = statusGeneration
  const request = getVisitorStatus()
    .then((status) => {
      const mapped = mapStatus(status)
      if (requestGeneration !== statusGeneration) {
        return authoritativeStatusAfter(requestGeneration)
      }
      cacheStatus = mapped
      cacheExpiresAt = Date.now() + CACHE_TTL_MS
      return mapped
    }, (error: unknown) => {
      if (requestGeneration !== statusGeneration) {
        return authoritativeStatusAfter(requestGeneration)
      }
      throw error
    })
    .finally(() => {
      if (pendingRequest?.promise === request) pendingRequest = null
    })
  pendingRequest = {
    generation: requestGeneration,
    promise: request,
  }
  return request
}

export async function obterStatusVisitante(force = false): Promise<StatusVisitante> {
  if (!force && cacheStatus && cacheExpiresAt > Date.now()) return cacheStatus
  if (pendingRequest) return pendingRequest.promise
  return refreshStatus()
}

export function recarregarStatusVisitante(): Promise<StatusVisitante> {
  limparCacheStatusVisitante()
  return refreshStatus()
}

export function limparCacheStatusVisitante() {
  statusGeneration += 1
  cacheStatus = null
  cacheExpiresAt = 0
  pendingRequest = null
}

export async function confirmarAceiteGlobal(
  originPath: string,
): Promise<StatusVisitante> {
  const accepted = await acceptGlobalAgeGate(originPath)
  if (!accepted.accepted) {
    throw new Error('AGE_GATE_ACCEPTANCE_NOT_CONFIRMED')
  }

  statusGeneration += 1
  pendingRequest = null
  cacheStatus = {
    ...(cacheStatus ?? failClosedStatus()),
    globalAccepted: true,
    state: accepted.state,
  }
  cacheExpiresAt = Date.now() + CACHE_TTL_MS

  try {
    const confirmed = await refreshStatus()
    if (!confirmed.globalAccepted) {
      throw new Error('AGE_GATE_ACCEPTANCE_NOT_CONFIRMED')
    }
    dispatchStatusChanged()
    return confirmed
  } catch (error) {
    limparCacheStatusVisitante()
    throw error
  }
}

function dispatchStatusChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AGE_VERIFICATION_CHANGED_EVENT))
  }
}

export function notificarMudancaVerificacao(status?: StatusVisitante) {
  statusGeneration += 1
  pendingRequest = null
  if (status) {
    cacheStatus = status
    cacheExpiresAt = Date.now() + CACHE_TTL_MS
  } else {
    cacheStatus = null
    cacheExpiresAt = 0
  }
  dispatchStatusChanged()
}
