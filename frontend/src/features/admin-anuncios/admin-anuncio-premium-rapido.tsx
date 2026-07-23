'use client'

import { useMemo, useRef, useState } from 'react'
import { Loader2, Sparkles } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import {
  activateAdminPremiumBatch,
  cancelAdminPremium,
  listAdminPremiumBenefits,
} from './api'
import type {
  AdminPremiumBenefit,
  AdminPremiumCatalogItem,
  AdminQueuePremiumBenefit,
} from './types'

function operationKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function active(item?: AdminQueuePremiumBenefit) {
  return Boolean(item && ['ATIVO', 'VENCENDO'].includes(item.status))
}

function pending(item?: AdminQueuePremiumBenefit) {
  return item?.status === 'PENDENTE'
}

export function queuePremiumBenefits(items: AdminPremiumBenefit[]): AdminQueuePremiumBenefit[] {
  return items.flatMap((item) => item.id && item.beneficioCodigo && item.beneficioNome
    ? [{
        ativacaoId: item.id,
        codigo: item.beneficioCodigo,
        nome: item.beneficioNome,
        status: item.statusCalculado,
        inicioEm: item.inicioEm,
        fimEm: item.fimEm,
      }]
    : [])
}

export function AdminAnuncioPremiumRapido({
  anuncioId,
  catalog,
  benefits,
  canManage,
  onChanged,
}: {
  anuncioId: string
  catalog: AdminPremiumCatalogItem[]
  benefits: AdminQueuePremiumBenefit[]
  canManage: boolean
  onChanged: (items: AdminQueuePremiumBenefit[]) => void
}) {
  const [openCode, setOpenCode] = useState<string | null>(null)
  const [duration, setDuration] = useState('')
  const [observation, setObservation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const idempotencyKey = useRef<string | null>(null)

  const latestByCode = useMemo(() => {
    const result = new Map<string, AdminQueuePremiumBenefit>()
    benefits.forEach((item) => result.set(item.codigo, item))
    return result
  }, [benefits])

  function open(item: AdminPremiumCatalogItem, nextOpen: boolean) {
    if (!canManage) return
    setOpenCode(nextOpen ? item.codigo : null)
    setError(null)
    setObservation('')
    idempotencyKey.current = null
    const first = item.opcoes.find((option) => option.ativo)
    setDuration(first ? String(first.duracaoDias) : '')
  }

  async function refreshRow() {
    onChanged(queuePremiumBenefits(await listAdminPremiumBenefits(anuncioId)))
  }

  async function activate(item: AdminPremiumCatalogItem) {
    const days = Number(duration)
    if (busy || !Number.isInteger(days) || days < 1 || observation.trim().length < 3) return
    setBusy(true)
    setError(null)
    const key = idempotencyKey.current ?? operationKey()
    idempotencyKey.current = key
    try {
      await activateAdminPremiumBatch(anuncioId, {
        beneficios: [{ beneficioId: item.id, duracaoDias: days }],
        observacao: observation.trim(),
      }, key)
      await refreshRow()
      idempotencyKey.current = null
      setOpenCode(null)
    } catch (reason) {
      setError(reason)
    } finally {
      setBusy(false)
    }
  }

  async function cancel(item: AdminQueuePremiumBenefit) {
    if (busy || observation.trim().length < 5) return
    setBusy(true)
    setError(null)
    const key = idempotencyKey.current ?? operationKey()
    idempotencyKey.current = key
    try {
      await cancelAdminPremium(item.ativacaoId, observation.trim(), key)
      await refreshRow()
      idempotencyKey.current = null
      setOpenCode(null)
    } catch (reason) {
      setError(reason)
    } finally {
      setBusy(false)
    }
  }

  if (catalog.length === 0) return <span className="text-xs text-zinc-500">Catálogo vazio</span>

  return (
    <div className="flex max-w-[280px] flex-wrap gap-1.5">
      {catalog.map((item) => {
        const latest = latestByCode.get(item.codigo)
        const isActive = active(latest)
        const isPending = pending(latest)
        const options = item.opcoes.filter((option) => option.ativo)
        const trigger = (
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={!canManage || busy || isPending || (!isActive && (!item.ativo || options.length === 0))}
            className={`h-7 max-w-full px-2 text-[11px] ${isActive ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : isPending ? 'border-amber-300 bg-amber-50 text-amber-800' : 'border-zinc-200 bg-white text-zinc-700'}`}
            title={canManage ? `${item.nome}: ${isActive ? 'ativo' : isPending ? 'pendente' : 'inativo'}` : `${item.nome}: somente leitura`}
          >
            <Sparkles className="mr-1 h-3 w-3 shrink-0" aria-hidden="true" />
            <span className="truncate">{item.nome}</span>
          </Button>
        )
        if (!canManage || isPending) return <span key={item.id}>{trigger}</span>
        return (
          <Popover key={item.id} open={openCode === item.codigo} onOpenChange={(value) => open(item, value)}>
            <PopoverTrigger asChild>{trigger}</PopoverTrigger>
            <PopoverContent align="start" className="w-80 rounded-md">
              <p className="font-semibold text-zinc-950">{item.nome}</p>
              <p className="mt-1 break-all text-xs text-zinc-500">{item.codigo} · {isActive ? latest?.status : 'INATIVO'}</p>
              {!isActive ? (
                <label className="mt-3 block">
                  <span className="mb-1 block text-xs font-semibold text-zinc-700">Duração</span>
                  <Select value={duration} disabled={busy} onValueChange={(value) => { setDuration(value); idempotencyKey.current = null }}>
                    <SelectTrigger className="w-full bg-white"><SelectValue placeholder="Escolha" /></SelectTrigger>
                    <SelectContent>{options.map((option) => <SelectItem key={option.id} value={String(option.duracaoDias)}>{option.duracaoDias} dia(s)</SelectItem>)}</SelectContent>
                  </Select>
                </label>
              ) : null}
              <label className="mt-3 block">
                <span className="mb-1 block text-xs font-semibold text-zinc-700">{isActive ? 'Motivo da desativação' : 'Observação administrativa'}</span>
                <Input value={observation} maxLength={500} disabled={busy} onChange={(event) => { setObservation(event.target.value); idempotencyKey.current = null }} />
              </label>
              {error ? <div className="mt-3"><ContractState error={error} compact /></div> : null}
              <Button
                type="button"
                size="sm"
                variant={isActive ? 'destructive' : 'default'}
                className="mt-3 w-full"
                disabled={busy || observation.trim().length < (isActive ? 5 : 3) || (!isActive && !duration)}
                onClick={() => latest && isActive ? void cancel(latest) : void activate(item)}
              >
                {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                {isActive ? 'Confirmar desativação' : 'Ativar sem débito'}
              </Button>
            </PopoverContent>
          </Popover>
        )
      })}
    </div>
  )
}
