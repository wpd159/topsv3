'use client'

import Link from 'next/link'
import { ReceiptText, WalletCards } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ReviewRow, StepPanel } from '@/features/anuncio-wizard/components/wizard-ui'
import type {
  AnuncioMeuResumo,
  MonetizacaoCotacaoDuracao,
  MonetizacaoCotacaoOpcao,
} from '../types'

type SelectedMonetizacaoItem = {
  opcao: MonetizacaoCotacaoOpcao
  duracao: MonetizacaoCotacaoDuracao
}

function durationLabel(dias: number) {
  return dias === 1 ? '1 dia' : `${dias} dias`
}

export function MonetizacaoStepResumo({
  anuncio,
  itens,
  saldoCreditos,
  totalCreditos,
  loading,
  onActivate,
}: {
  anuncio: AnuncioMeuResumo
  itens: SelectedMonetizacaoItem[]
  saldoCreditos: number
  totalCreditos: number
  loading: boolean
  onActivate: () => void
}) {
  const saldoApos = saldoCreditos - totalCreditos
  const saldoSuficiente = saldoApos >= 0

  return (
    <StepPanel>
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50">
          <ReceiptText className="h-5 w-5 text-zinc-500" />
        </div>
        <div>
          <h3 className="text-lg font-semibold text-zinc-950">Resumo da compra</h3>
          <p className="text-sm text-zinc-500">
            Nenhum crédito será descontado antes da confirmação.
          </p>
        </div>
      </div>

      <div className="mb-4 rounded-2xl border border-zinc-200 bg-zinc-50 px-4 py-4">
        <p className="text-xs font-semibold uppercase text-zinc-500">Anúncio</p>
        <p className="mt-1 font-semibold text-zinc-950">{anuncio.titulo}</p>
        <p className="mt-1 text-xs text-zinc-500">/{anuncio.slug}</p>
      </div>

      <div className="space-y-3">
        {itens.map((item) => (
          <div key={item.opcao.codigo} className="rounded-[24px] border border-zinc-200 bg-white px-4 py-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-zinc-950">{item.opcao.titulo}</p>
                <p className="mt-1 text-xs text-zinc-500">{item.opcao.descricao}</p>
              </div>
              <div className="rounded-full bg-zinc-950 px-3 py-1 text-xs font-semibold text-white">
                {item.duracao.creditos} créditos
              </div>
            </div>
            <p className="mt-3 text-sm text-zinc-600">Validade: {durationLabel(item.duracao.dias)}</p>
          </div>
        ))}
      </div>

      <div className="mt-5 grid gap-3 sm:grid-cols-3">
        <ReviewRow label="Custo total" value={`${totalCreditos} créditos`} />
        <ReviewRow label="Saldo atual" value={`${saldoCreditos} créditos`} />
        <ReviewRow
          label={saldoSuficiente ? 'Saldo restante' : 'Créditos faltantes'}
          value={`${Math.abs(saldoApos)} créditos`}
        />
      </div>

      <div className="mt-5 flex flex-col gap-3 rounded-2xl border border-[#FC1EAD]/20 bg-[#fff0f8] p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-3">
          <WalletCards className="mt-0.5 h-5 w-5 shrink-0 text-[#FC1EAD]" />
          <p className="text-sm leading-6 text-zinc-700">
            {saldoSuficiente
              ? 'Confirme uma única vez para debitar o ledger e ativar todos os itens.'
              : 'O saldo atual não cobre esta seleção. Ajuste os benefícios ou adquira créditos.'}
          </p>
        </div>
        {saldoSuficiente ? (
          <Button
            type="button"
            onClick={onActivate}
            disabled={loading}
            className="h-11 shrink-0 bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          >
            {loading ? 'Ativando...' : 'Confirmar ativação'}
          </Button>
        ) : (
          <Button asChild variant="outline" className="h-11 shrink-0 bg-white">
            <Link href="/creditos">Ver créditos</Link>
          </Button>
        )}
      </div>
    </StepPanel>
  )
}
