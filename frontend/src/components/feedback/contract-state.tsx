'use client'

import { useCallback, useState } from 'react'
import { AlertCircle, RefreshCw } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { ApiContractError, BackendContractPendingError, normalizeApiError } from '@/lib/api-contract'

type ContractStateProps = {
  error: unknown
  onRetry?: () => void
  compact?: boolean
}

export function ContractState({ error, onRetry, compact = false }: ContractStateProps) {
  const normalized = normalizeApiError(error)
  const pending = normalized instanceof BackendContractPendingError
  const title = pending ? 'Integracao pendente' : 'Nao foi possivel carregar'

  return (
    <div
      role="status"
      className={`flex items-start gap-3 border border-amber-300 bg-amber-50 text-amber-900 ${
        compact ? 'p-3 text-sm' : 'p-4'
      }`}
    >
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        <p className="mt-1 text-amber-800">{normalized.message}</p>
      </div>
      {onRetry && normalized.retryable ? (
        <Button type="button" size="sm" variant="outline" onClick={onRetry} className="shrink-0">
          <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
          Tentar novamente
        </Button>
      ) : null}
    </div>
  )
}

export function pendingContractError(module: string) {
  return new BackendContractPendingError(module)
}

export function usePendingContractActions(module: string) {
  const [attemptedAction, setAttemptedAction] = useState<string | null>(null)
  const runPendingAction = useCallback((action: string) => {
    setAttemptedAction(action)
  }, [])

  return {
    error: pendingContractError(module),
    attemptedAction,
    runPendingAction,
  }
}

export function PendingActionFeedback({ attemptedAction }: { attemptedAction: string | null }) {
  if (!attemptedAction) return null

  return (
    <p role="alert" className="text-sm font-medium text-amber-800">
      <span className="font-mono text-xs">CONTRATO_BACKEND_AUSENTE</span>: {attemptedAction} nao foi executada porque o
      contrato backend V3 ainda nao esta disponivel.
    </p>
  )
}

export function isExplicitContractError(error: unknown): error is ApiContractError {
  return error instanceof ApiContractError
}
