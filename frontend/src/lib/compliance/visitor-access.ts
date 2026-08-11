"use client"

import {
  getVisitorStatus,
  type VisitorAccessStatus,
} from '@/lib/compliance/age-gate-api'

export type StatusVisitante = {
  globalAccepted?: boolean
  verified: boolean
  level?: string | null
  expiresAt?: string | null
  explicitVerified?: boolean
  explicitLevel?: string | null
  explicitExpiresAt?: string | null
  state?: string | null
  riskScore?: number | null
  decision?: string | null
  documentStatus?: string | null
  reasonPublic?: string | null
}

let cacheStatus: StatusVisitante | null = null
let cacheExpiresAt = 0
let pendingRequest: Promise<StatusVisitante> | null = null
let statusGeneration = 0

const CACHE_TTL_MS = 30_000
export const AGE_VERIFICATION_CHANGED_EVENT = 'topsv3:age-verification-changed'

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

function refreshStatus() {
  const requestGeneration = statusGeneration
  const request = getVisitorStatus()
    .then((status) => {
      const mapped = mapStatus(status)
      if (requestGeneration !== statusGeneration) return cacheStatus ?? { verified: false }
      cacheStatus = mapped
      cacheExpiresAt = Date.now() + CACHE_TTL_MS
      return mapped
    })
    .finally(() => {
      if (pendingRequest === request) pendingRequest = null
    })
  pendingRequest = request
  return request
}

export async function obterStatusVisitante(force = false): Promise<StatusVisitante> {
  if (!force && cacheStatus && cacheExpiresAt > Date.now()) return cacheStatus
  if (pendingRequest) return pendingRequest
  return refreshStatus()
}

export function limparCacheStatusVisitante() {
  statusGeneration += 1
  cacheStatus = null
  cacheExpiresAt = 0
  pendingRequest = null
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
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(AGE_VERIFICATION_CHANGED_EVENT))
  }
}
