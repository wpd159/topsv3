'use client'

import { useMemo } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { Copy, CreditCard, QrCode, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { CheckoutData, PlanoCredito } from '../types'

function brl(value: number) {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function formatPlanoCredits(plan: PlanoCredito) {
  return `${plan.creditos} créditos`
}

export function MonetizacaoStepPix({
  planos,
  creditosFaltantes,
  selectedPlanoId,
  onSelectPlano,
  nomeCompleto,
  cpf,
  onNomeCompletoChange,
  onCpfChange,
  creatingCheckout,
  checkout,
  polling,
  onCreateCheckout,
  onVerifyPayment,
  onCopyPix,
}: {
  planos: PlanoCredito[]
  creditosFaltantes: number
  selectedPlanoId: number | null
  onSelectPlano: (planId: number) => void
  nomeCompleto: string
  cpf: string
  onNomeCompletoChange: (value: string) => void
  onCpfChange: (value: string) => void
  creatingCheckout: boolean
  checkout: CheckoutData | null
  polling: boolean
  onCreateCheckout: () => void
  onVerifyPayment: () => void
  onCopyPix: () => void
}) {
  const highlightedPlan = useMemo(() => {
    return (
      planos.find((plan) => plan.id === selectedPlanoId) ||
      planos.find((plan) => plan.creditos >= creditosFaltantes) ||
      planos[0] ||
      null
    )
  }, [creditosFaltantes, planos, selectedPlanoId])

  return (
    <div className="space-y-6">
      <div className="rounded-[28px] border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <h3 className="text-lg font-semibold text-zinc-950">Escolha o plano de créditos</h3>
        <p className="mt-1 text-sm leading-6 text-zinc-600">
          O checkout continua usando o fluxo atual do Pix. Aqui só organizamos a experiência dentro do wizard.
        </p>

        <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {planos.map((plan) => {
            const selected = highlightedPlan?.id === plan.id
            const enough = plan.creditos >= creditosFaltantes
            return (
              <button
                key={plan.id}
                type="button"
                onClick={() => onSelectPlano(plan.id)}
                className={[
                  'rounded-[24px] border px-4 py-4 text-left transition-all duration-300',
                  selected
                    ? 'border-zinc-950 bg-zinc-950 text-white shadow-[0_16px_32px_rgba(24,24,27,0.16)]'
                    : 'border-zinc-200 bg-white hover:border-zinc-300 hover:shadow-[0_12px_28px_rgba(24,24,27,0.08)]',
                ].join(' ')}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold">{plan.nome}</p>
                    <p className={selected ? 'mt-1 text-xs text-zinc-300' : 'mt-1 text-xs text-zinc-500'}>
                      {plan.descricao}
                    </p>
                  </div>
                  <div className="rounded-full border px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.16em]">
                    {enough ? 'Suficiente' : 'Complemento'}
                  </div>
                </div>

                <div className="mt-5 flex items-end justify-between gap-3">
                  <div>
                    <p className={selected ? 'text-sm text-zinc-300' : 'text-sm text-zinc-500'}>
                      {formatPlanoCredits(plan)}
                    </p>
                    <p className="mt-1 text-2xl font-semibold">{brl(plan.valor)}</p>
                  </div>
                  <CreditCard className={selected ? 'h-5 w-5 text-white' : 'h-5 w-5 text-zinc-500'} />
                </div>
              </button>
            )
          })}
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,0.9fr)_420px]">
        <div className="rounded-[28px] border border-zinc-200 bg-white px-5 py-5 shadow-sm">
          <h3 className="text-lg font-semibold text-zinc-950">Identificacao do pagamento</h3>
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label>Nome completo</Label>
              <Input value={nomeCompleto} onChange={(e) => onNomeCompletoChange(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>CPF</Label>
              <Input value={cpf} onChange={(e) => onCpfChange(e.target.value)} />
            </div>
          </div>

          <div className="mt-5">
            <Button
              type="button"
              onClick={onCreateCheckout}
              disabled={creatingCheckout || !highlightedPlan}
              className="h-12 rounded-2xl bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
            >
              {creatingCheckout ? 'Gerando Pix...' : 'Gerar Pix'}
            </Button>
          </div>
        </div>

        <div className="rounded-[28px] border border-zinc-200 bg-zinc-950 px-5 py-5 text-white shadow-[0_22px_50px_rgba(24,24,27,0.18)]">
          <div className="flex items-start gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-white/10 bg-white/10">
              <QrCode className="h-5 w-5 text-white" />
            </div>
            <div>
              <h3 className="text-lg font-semibold">Pix do wizard</h3>
              <p className="mt-1 text-sm leading-6 text-zinc-300">
                Depois da aprovação, o wizard volta a verificar o saldo e conclui a ativação.
              </p>
            </div>
          </div>

          {checkout ? (
            <div className="mt-5 space-y-4">
              <div className="rounded-[24px] border border-white/10 bg-white/10 px-4 py-4">
                <p className="text-xs uppercase tracking-[0.16em] text-zinc-300">Status</p>
                <p className="mt-2 text-lg font-semibold">{checkout.statusEfetivo || checkout.status}</p>
              </div>

              <div className="rounded-[24px] border border-white/10 bg-white px-4 py-4 text-zinc-950">
                <div className="flex justify-center">
                  <QRCodeSVG value={checkout.pixCopiaECola} size={180} />
                </div>
                <div className="mt-4 rounded-2xl bg-zinc-50 px-3 py-3 text-xs leading-6 text-zinc-600">
                  {checkout.pixCopiaECola}
                </div>
                <div className="mt-4 flex flex-col gap-2">
                  <Button type="button" onClick={onCopyPix} className="h-11 rounded-2xl bg-zinc-950 text-white hover:bg-zinc-800">
                    <Copy className="mr-2 h-4 w-4" />
                    Copiar código Pix
                  </Button>
                  <Button type="button" variant="outline" onClick={onVerifyPayment} disabled={polling} className="h-11 rounded-2xl border-white/20 bg-white/5 text-white hover:bg-white/10">
                    <RefreshCw className="mr-2 h-4 w-4" />
                    {polling ? 'Verificando...' : 'Ja paguei, verificar agora'}
                  </Button>
                </div>
              </div>
            </div>
          ) : (
            <div className="mt-5 rounded-[24px] border border-white/10 bg-white/10 px-4 py-4 text-sm leading-6 text-zinc-300">
              Escolha um plano e gere o Pix para seguir.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
