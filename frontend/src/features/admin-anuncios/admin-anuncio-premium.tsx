'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Loader2, Sparkles } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import {
  activateAdminPremium,
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

export function AdminAnuncioPremium({
  anuncioId,
  canManage,
}: {
  anuncioId: string
  canManage: boolean
}) {
  const [benefits, setBenefits] = useState<AdminPremiumBenefit[]>([])
  const [catalog, setCatalog] = useState<AdminPremiumCatalogItem[]>([])
  const [benefitId, setBenefitId] = useState('')
  const [duration, setDuration] = useState('')
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
        canManage ? listAdminPremiumCatalog() : Promise.resolve([]),
      ])
      setBenefits(current)
      setCatalog(available.filter((item) => item.ativo && item.escopo === 'ANUNCIO'))
    } catch (reason) {
      setError(reason)
    } finally {
      setLoading(false)
    }
  }, [anuncioId, canManage])

  useEffect(() => { void load() }, [load])

  const selected = useMemo(() => catalog.find((item) => item.id === benefitId), [benefitId, catalog])
  const options = selected?.opcoes.filter((item) => item.ativo) ?? []

  async function activate() {
    if (busy || !benefitId || !duration || observation.trim().length < 3) return
    setBusy(true)
    setError(null)
    const idempotencyKey = activationKey.current ?? operationKey()
    activationKey.current = idempotencyKey
    try {
      await activateAdminPremium(anuncioId, {
        beneficioId: benefitId,
        duracaoDias: Number(duration),
        observacao: observation.trim(),
      }, idempotencyKey)
      activationKey.current = null
      setBenefitId('')
      setDuration('')
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
        <div><h3 className="flex items-center gap-2 font-semibold text-zinc-950"><Sparkles className="h-4 w-4 text-pink-700" />Benefícios</h3><p className="mt-1 text-sm text-zinc-600">Ativações vigentes, agendadas e encerradas, calculadas pelo backend.</p></div>
        <Button type="button" size="sm" variant="outline" onClick={() => void load()} disabled={loading || busy}>Atualizar</Button>
      </div>
      {error ? <ContractState error={error} onRetry={() => void load()} compact /> : null}
      {loading ? <p className="flex items-center gap-2 text-sm text-zinc-500"><Loader2 className="h-4 w-4 animate-spin" />Carregando benefícios...</p> : null}
      {!loading && benefits.length === 0 ? <p className="text-sm text-zinc-600">Nenhuma ativação Premium registrada.</p> : null}
      <div className="divide-y divide-zinc-200 border-y border-zinc-200">
        {benefits.map((item) => {
          const cancellable = item.statusOriginal === 'ATIVA' || item.statusOriginal === 'AGENDADA'
          return (
            <article key={item.id} className="grid gap-3 py-4 lg:grid-cols-[minmax(0,1fr)_220px]">
              <div>
                <div className="flex flex-wrap items-center gap-2"><strong className="text-sm text-zinc-950">{item.beneficioNome || item.beneficioCodigo}</strong><Badge variant="outline">{item.statusCalculado}</Badge><Badge variant="outline">{item.origem || 'Origem indisponível'}</Badge></div>
                <p className="mt-2 text-xs text-zinc-600">{item.duracaoDias ?? '—'} dia(s) · {formatDate(item.inicioEm)} até {formatDate(item.fimEm)}</p>
                {item.observacao ? <p className="mt-2 text-sm text-zinc-700">{item.observacao}</p> : null}
              </div>
              {canManage && cancellable ? (
                <div className="flex flex-col gap-2"><Input value={cancelReasons[item.id] || ''} onChange={(event) => { delete cancellationKeys.current[item.id]; setCancelReasons((current) => ({ ...current, [item.id]: event.target.value })) }} placeholder="Motivo da desativação" maxLength={500} /><Button type="button" size="sm" variant="outline" disabled={busy || (cancelReasons[item.id]?.trim().length || 0) < 5} onClick={() => void cancel(item)}>Desativar</Button></div>
              ) : null}
            </article>
          )
        })}
      </div>
      {canManage ? (
        <section className="rounded-md border border-zinc-200 bg-zinc-50 p-4">
          <h4 className="text-sm font-semibold text-zinc-950">Ativação administrativa</h4>
          <p className="mt-1 text-xs text-zinc-600">Não debita créditos. Benefícios e durações vêm do catálogo vigente.</p>
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <Select value={benefitId} onValueChange={(value) => { activationKey.current = null; setBenefitId(value); setDuration('') }}><SelectTrigger className="w-full bg-white"><SelectValue placeholder="Escolha o benefício" /></SelectTrigger><SelectContent>{catalog.map((item) => <SelectItem key={item.id} value={item.id}>{item.nome}</SelectItem>)}</SelectContent></Select>
            <Select value={duration} onValueChange={(value) => { activationKey.current = null; setDuration(value) }} disabled={!benefitId}><SelectTrigger className="w-full bg-white"><SelectValue placeholder="Escolha a duração" /></SelectTrigger><SelectContent>{options.map((item) => <SelectItem key={item.id} value={String(item.duracaoDias)}>{item.duracaoDias} dia(s)</SelectItem>)}</SelectContent></Select>
            <Input className="md:col-span-2" value={observation} onChange={(event) => { activationKey.current = null; setObservation(event.target.value) }} placeholder="Observação obrigatória" maxLength={500} />
          </div>
          <Button type="button" className="mt-3" disabled={busy || !benefitId || !duration || observation.trim().length < 3} onClick={() => void activate()}>{busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}Ativar benefício</Button>
        </section>
      ) : <p className="text-sm font-medium text-zinc-600">MODERADOR possui acesso somente para leitura.</p>}
    </div>
  )
}
