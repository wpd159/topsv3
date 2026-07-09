'use client'

import Link from 'next/link'
import { BanknoteArrowUp, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'

export function MonetizacaoStepSaldo({
  saldoCreditos,
  totalCreditos,
  loading,
  onActivate,
}: {
  saldoCreditos: number
  totalCreditos: number
  loading: boolean
  onActivate: () => void
}) {
  const saldoSuficiente = saldoCreditos >= totalCreditos
  const saldoApos = Math.max(0, saldoCreditos - totalCreditos)
  const faltam = Math.max(0, totalCreditos - saldoCreditos)

  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(0,1.1fr)_380px]">
      <div className="rounded-[28px] border border-zinc-200 bg-white px-5 py-5 shadow-sm">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50">
            <Wallet className="h-5 w-5 text-zinc-500" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-zinc-950">Pagamento com créditos</h3>
            <p className="mt-1 text-sm leading-6 text-zinc-600">
              A ativação usa o saldo de créditos existente. Nenhuma regra de cobrança foi alterada.
            </p>
          </div>
        </div>

        <div className="mt-5 grid gap-4 sm:grid-cols-3">
          <div className="rounded-[24px] border border-zinc-200 bg-zinc-50 px-4 py-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-500">Saldo atual</p>
            <p className="mt-3 text-2xl font-semibold text-zinc-950">{saldoCreditos}</p>
          </div>
          <div className="rounded-[24px] border border-zinc-200 bg-zinc-50 px-4 py-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-500">Total</p>
            <p className="mt-3 text-2xl font-semibold text-zinc-950">{totalCreditos}</p>
          </div>
          <div className="rounded-[24px] border border-zinc-200 bg-zinc-50 px-4 py-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-500">
              {saldoSuficiente ? 'Saldo após compra' : 'Faltam'}
            </p>
            <p className="mt-3 text-2xl font-semibold text-zinc-950">
              {saldoSuficiente ? saldoApos : faltam}
            </p>
          </div>
        </div>
      </div>

      <div className="rounded-[28px] border border-[#FC1EAD]/20 bg-[#fff0f8] px-5 py-5 shadow-sm">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-[#FC1EAD]/20 bg-white text-[#FC1EAD]">
            <BanknoteArrowUp className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-lg font-semibold text-zinc-950">Próximo passo</h3>
            <p className="mt-1 text-sm leading-6 text-zinc-700">
              {saldoSuficiente
                ? 'Seu saldo cobre a compra. Confirme para ativar os benefícios selecionados.'
                : 'Seu saldo ainda não cobre esta compra. Compre créditos e depois volte para concluir.'}
            </p>
          </div>
        </div>

        <div className="mt-6">
          {saldoSuficiente ? (
            <Button
              type="button"
              onClick={onActivate}
              disabled={loading}
              className="h-12 w-full rounded-2xl bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
            >
              {loading ? 'Ativando...' : 'Confirmar ativação'}
            </Button>
          ) : (
            <Button asChild className="h-12 w-full rounded-2xl bg-[#FC1EAD] text-white hover:bg-[#e01a9a]">
              <Link href="/planos-e-creditos">Comprar créditos</Link>
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
