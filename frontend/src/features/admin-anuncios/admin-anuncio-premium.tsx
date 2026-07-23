'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Loader2, Sparkles } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import {
  activateAdminPremiumBatch,
  cancelAdminPremium,
  listAdminPremiumBenefits,
  listAdminPremiumCatalog,
} from './api'
import type { AdminPremiumBenefit, AdminPremiumCatalogItem } from './types'

function operationKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function formatDate(value?: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function isCurrent(item: AdminPremiumBenefit) {
  return ['ATIVO', 'VENCENDO', 'PENDENTE'].includes(item.statusCalculado)
}

export function AdminAnuncioPremium({
  anuncioId,
  canManage,
  disabledReason,
}: {
  anuncioId: string
  canManage: boolean
  disabledReason?: string
}) {
  const [benefits, setBenefits] = useState<AdminPremiumBenefit[]>([])
  const [catalog, setCatalog] = useState<AdminPremiumCatalogItem[]>([])
  const [selected, setSelected] = useState<Record<string, boolean>>({})
  const [durations, setDurations] = useState<Record<string, string>>({})
  const [observation, setObservation] = useState('')
  const [cancelReasons, setCancelReasons] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const activationKey = useRef<string | null>(null)
  const cancellationKeys = useRef<Record<string, string>>({})

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [current, available] = await Promise.all([
        listAdminPremiumBenefits(anuncioId),
        listAdminPremiumCatalog(),
      ])
      setBenefits(current)
      setCatalog(available.filter((item) => item.escopo === 'ANUNCIO'))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId])

  useEffect(() => { void load() }, [load])

  const latestByCode = useMemo(() => {
    const result = new Map<string, AdminPremiumBenefit>()
    benefits.forEach((item) => {
      if (item.beneficioCodigo) result.set(item.beneficioCodigo, item)
    })
    return result
  }, [benefits])

  const currentByCode = useMemo(() => {
    const result = new Map<string, AdminPremiumBenefit>()
    benefits.filter(isCurrent).forEach((item) => {
      if (item.beneficioCodigo) result.set(item.beneficioCodigo, item)
    })
    return result
  }, [benefits])

  const selectedItems = useMemo(() => catalog.flatMap((item) => {
    const duration = Number(durations[item.id])
    return selected[item.id] && Number.isInteger(duration) && duration > 0
      ? [{ beneficioId: item.id, duracaoDias: duration }]
      : []
  }), [catalog, durations, selected])

  function changeSelection(item: AdminPremiumCatalogItem, checked: boolean) {
    activationKey.current = null
    setSelected((current) => ({ ...current, [item.id]: checked }))
    if (checked && !durations[item.id]) {
      const first = item.opcoes.filter((option) => option.ativo)[0]
      if (first) setDurations((current) => ({ ...current, [item.id]: String(first.duracaoDias) }))
    }
  }

  async function activate() {
    if (busy || selectedItems.length === 0) return
    setBusy(true)
    setError(null)
    const idempotencyKey = activationKey.current ?? operationKey()
    activationKey.current = idempotencyKey
    try {
      await activateAdminPremiumBatch(anuncioId, {
        beneficios: selectedItems,
        observacao: observation.trim() || null,
      }, idempotencyKey)
      activationKey.current = null
      setSelected({})
      setDurations({})
      setObservation('')
      await load()
    } catch (reason) {
      setError(reason)
    } finally {
      setBusy(false)
    }
  }

  async function cancel(item: AdminPremiumBenefit) {
    const reason = cancelReasons[item.id]?.trim() || ''
    if (busy || reason.length < 5) return
    setBusy(true)
    setError(null)
    const idempotencyKey = cancellationKeys.current[item.id] ?? operationKey()
    cancellationKeys.current[item.id] = idempotencyKey
    try {
      await cancelAdminPremium(item.id, reason, idempotencyKey)
      delete cancellationKeys.current[item.id]
      await load()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="flex items-center gap-2 font-semibold text-zinc-950"><Sparkles className="h-4 w-4 text-pink-700" />Benefícios</h3>
          <p className="mt-1 text-sm text-zinc-600">Catálogo, durações e estados calculados pelo backend.</p>
        </div>
        <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading || busy}>Atualizar</Button>
      </div>

      {error ? <ContractState error={error} onRetry={() => void load()} compact /> : null}
      {loading ? <p className="flex items-center gap-2 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando benefícios...</p> : null}
      {!loading && catalog.length === 0 ? <p className="text-sm text-zinc-600">O catálogo de benefícios está vazio.</p> : null}

      <div className="grid gap-3 lg:grid-cols-2">
        {catalog.map((item) => {
          const options = item.opcoes.filter((option) => option.ativo)
          const current = currentByCode.get(item.codigo)
          const latest = latestByCode.get(item.codigo)
          const selectable = canManage && item.ativo && options.length > 0 && !current
          return (
            <article key={item.id} className={`border p-4 ${selected[item.id] ? 'border-pink-400 bg-pink-50/40' : 'border-zinc-200 bg-white'}`}>
              <div className="flex items-start gap-3">
                <input
                  id={`premium-${item.id}`}
                  type="checkbox"
                  checked={Boolean(selected[item.id])}
                  disabled={!selectable || busy}
                  onChange={(event) => changeSelection(item, event.target.checked)}
                  className="mt-1 h-4 w-4 accent-pink-600"
                />
                <label htmlFor={`premium-${item.id}`} className={`min-w-0 flex-1 ${selectable ? 'cursor-pointer' : ''}`}>
                  <span className="block font-semibold text-zinc-950">{item.nome}</span>
                  <span className="mt-1 block break-all text-xs font-medium text-zinc-500">{item.codigo}</span>
                </label>
                <Badge variant="outline">{latest?.statusCalculado || (item.ativo ? 'NÃO ATIVO' : 'CATÁLOGO INATIVO')}</Badge>
              </div>
              {item.descricao ? <p className="mt-3 text-sm leading-5 text-zinc-600">{item.descricao}</p> : null}
              <div className="mt-3">
                <span className="mb-1 block text-xs font-semibold text-zinc-600">Duração permitida</span>
                <Select
                  value={durations[item.id] || ''}
                  disabled={!selectable || !selected[item.id] || busy}
                  onValueChange={(value) => {
                    activationKey.current = null
                    setDurations((values) => ({ ...values, [item.id]: value }))
                  }}
                >
                  <SelectTrigger className="w-full bg-white"><SelectValue placeholder={options.length ? 'Escolha a duração' : 'Sem duração ativa'} /></SelectTrigger>
                  <SelectContent>{options.map((option) => <SelectItem key={option.id} value={String(option.duracaoDias)}>{option.duracaoDias} dia(s)</SelectItem>)}</SelectContent>
                </Select>
              </div>
              {current ? (
                <div className="mt-3 border-t border-zinc-100 pt-3 text-xs text-zinc-600">
                  <p>{formatDate(current.inicioEm)} até {formatDate(current.fimEm)}</p>
                  {canManage ? (
                    <div className="mt-2 flex flex-col gap-2 sm:flex-row">
                      <Input
                        value={cancelReasons[current.id] || ''}
                        onChange={(event) => {
                          delete cancellationKeys.current[current.id]
                          setCancelReasons((values) => ({ ...values, [current.id]: event.target.value }))
                        }}
                        placeholder="Motivo da desativação"
                        maxLength={500}
                      />
                      <Button type="button" size="sm" variant="outline" disabled={busy || (cancelReasons[current.id]?.trim().length || 0) < 5} onClick={() => void cancel(current)}>Desativar</Button>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </article>
          )
        })}
      </div>

      {canManage ? (
        <section className="border-t border-zinc-200 pt-4">
          <h4 className="text-sm font-semibold text-zinc-950">Ativação administrativa</h4>
          <p className="mt-1 text-xs text-zinc-600">Não debita créditos. Somente os benefícios marcados serão ativados.</p>
          <Input
            className="mt-3"
            value={observation}
            onChange={(event) => { activationKey.current = null; setObservation(event.target.value) }}
            placeholder="Observação administrativa (opcional)"
            maxLength={500}
          />
          <Button type="button" className="mt-3" disabled={busy || selectedItems.length === 0} onClick={() => void activate()}>
            {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Ativar selecionados ({selectedItems.length})
          </Button>
        </section>
      ) : <p className="text-sm font-medium text-zinc-600">{disabledReason || 'MODERADOR possui acesso somente para leitura.'}</p>}

      {benefits.length > 0 ? (
        <details className="border-t border-zinc-200 pt-4">
          <summary className="cursor-pointer text-sm font-semibold text-zinc-800">Histórico de ativações ({benefits.length})</summary>
          <div className="mt-3 divide-y divide-zinc-200 border-y border-zinc-200">
            {benefits.map((item) => (
              <div key={item.id} className="py-3 text-sm">
                <div className="flex flex-wrap items-center gap-2"><strong>{item.beneficioNome || item.beneficioCodigo}</strong><Badge variant="outline">{item.statusCalculado}</Badge></div>
                <p className="mt-1 text-xs text-zinc-600">{formatDate(item.inicioEm)} até {formatDate(item.fimEm)} · {item.origem || 'Origem indisponível'}</p>
              </div>
            ))}
          </div>
        </details>
      ) : null}
    </div>
  )
}
