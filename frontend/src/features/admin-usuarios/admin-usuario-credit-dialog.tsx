'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowDownCircle, ArrowUpCircle, Loader2, WalletCards } from 'lucide-react'

import { ContractState } from '@/components/feedback/contract-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  AdminCreditosApi,
  type AdminCreditoMovimento,
  type AdminCreditoSaldo,
} from '@/lib/admin-creditos-operacionais-api'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  usuarioId: string
  nome: string
}

function pretty(value: string) {
  return value.replaceAll('_', ' ').toLocaleLowerCase('pt-BR').replace(/^./, (letter) => letter.toUpperCase())
}

function dateTime(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}

export function AdminUsuarioCreditDialog({
  open,
  onOpenChange,
  usuarioId,
  nome,
}: Props) {
  const [saldo, setSaldo] = useState<AdminCreditoSaldo | null>(null)
  const [movimentos, setMovimentos] = useState<AdminCreditoMovimento[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [mode, setMode] = useState<'CREDITO' | 'DEBITO'>('CREDITO')
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const actionLock = useRef(false)
  const idempotencyKey = useRef(`ajuste-usuario-${typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Date.now()}`)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [balance, history] = await Promise.all([
        AdminCreditosApi.saldo(usuarioId),
        AdminCreditosApi.movimentos(usuarioId),
      ])
      setSaldo(balance)
      setMovimentos(history.itens)
    } catch (reasonError) {
      setError(reasonError)
    } finally {
      setLoading(false)
    }
  }, [usuarioId])

  useEffect(() => {
    if (open) void load()
  }, [load, open])

  async function adjust() {
    if (actionLock.current) return
    const amount = Number(quantity)
    if (!Number.isInteger(amount) || amount <= 0) {
      setError(new Error('Informe uma quantidade positiva de creditos.'))
      return
    }
    if (reason.trim().length < 5) {
      setError(new Error('Informe um motivo com pelo menos cinco caracteres.'))
      return
    }
    const action = mode === 'CREDITO' ? 'adicionar' : 'remover'
    if (!window.confirm(`Confirma ${action} ${amount} credito(s) para ${nome}?`)) return
    actionLock.current = true
    setBusy(true)
    setError(null)
    try {
      await AdminCreditosApi.ajustar(
        usuarioId,
        mode,
        amount,
        reason.trim(),
        idempotencyKey.current,
      )
      setQuantity('')
      setReason('')
      idempotencyKey.current = `ajuste-usuario-${typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Date.now()}`
      await load()
    } catch (reasonError) {
      setError(reasonError)
    } finally {
      actionLock.current = false
      setBusy(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!busy) onOpenChange(next) }}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <WalletCards className="h-5 w-5" />
            Creditos de {nome}
          </DialogTitle>
          <DialogDescription>Saldo e lancamentos do ledger canonico.</DialogDescription>
        </DialogHeader>

        {error ? <ContractState error={error} onRetry={() => void load()} compact /> : null}
        {loading && !saldo ? (
          <p className="flex items-center justify-center gap-2 py-10 text-sm text-zinc-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Carregando creditos...
          </p>
        ) : null}

        {saldo ? (
          <>
            <div className="rounded-md border border-pink-200 bg-pink-50 px-5 py-4">
              <p className="text-xs font-semibold uppercase text-pink-700">Saldo atual</p>
              <p className="mt-1 text-3xl font-bold text-zinc-950">{saldo.saldoProjetado.toLocaleString('pt-BR')}</p>
            </div>

            <section className="space-y-4">
              <div className="grid grid-cols-2 gap-2">
                <Button type="button" variant={mode === 'CREDITO' ? 'default' : 'outline'} onClick={() => setMode('CREDITO')}>
                  <ArrowUpCircle className="mr-2 h-4 w-4" /> Adicionar
                </Button>
                <Button type="button" variant={mode === 'DEBITO' ? 'destructive' : 'outline'} onClick={() => setMode('DEBITO')}>
                  <ArrowDownCircle className="mr-2 h-4 w-4" /> Remover
                </Button>
              </div>
              <div className="grid gap-4 sm:grid-cols-[10rem_minmax(0,1fr)]">
                <div className="space-y-2">
                  <Label htmlFor="admin-user-credit-quantity">Quantidade</Label>
                  <Input
                    id="admin-user-credit-quantity"
                    inputMode="numeric"
                    value={quantity}
                    onChange={(event) => setQuantity(event.target.value.replace(/\D/g, ''))}
                    disabled={busy}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="admin-user-credit-reason">Motivo</Label>
                  <Input
                    id="admin-user-credit-reason"
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    maxLength={500}
                    disabled={busy}
                  />
                </div>
              </div>
              <Button type="button" disabled={busy} onClick={() => void adjust()}>
                {busy ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                Confirmar {mode === 'CREDITO' ? 'adicao' : 'remocao'}
              </Button>
            </section>

            <section>
              <h3 className="font-semibold text-zinc-950">Historico</h3>
              {movimentos.length === 0 ? (
                <p className="mt-3 border-y border-zinc-200 py-8 text-center text-sm text-zinc-500">Nenhum lancamento.</p>
              ) : (
                <div className="mt-3 divide-y divide-zinc-200 border-y border-zinc-200">
                  {movimentos.map((movement) => (
                    <article key={movement.id} className="grid gap-2 py-3 text-sm sm:grid-cols-[9rem_1fr_auto] sm:items-center">
                      <div>
                        <p className="font-medium text-zinc-900">{dateTime(movement.criadoEm)}</p>
                        <p className="text-xs text-zinc-500">{movement.administradorId ? 'Operador administrativo' : pretty(movement.origem)}</p>
                      </div>
                      <div className="min-w-0">
                        <p className="font-medium text-zinc-900">{pretty(movement.natureza)}</p>
                        <p className="break-words text-xs text-zinc-500">{movement.motivo || 'Sem motivo registrado'}</p>
                      </div>
                      <div className="flex items-center justify-between gap-3 sm:block sm:text-right">
                        <Badge variant={movement.direcao === 'DEBITO' ? 'destructive' : 'outline'}>
                          {movement.direcao === 'DEBITO' ? '-' : '+'}{movement.quantidade}
                        </Badge>
                        <p className="mt-1 text-xs text-zinc-500">Saldo {movement.saldoDepois}</p>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>Fechar</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
