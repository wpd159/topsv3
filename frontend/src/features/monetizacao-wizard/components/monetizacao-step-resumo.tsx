'use client'

import { ReceiptText } from 'lucide-react'
import { ReviewRow, StepPanel } from '@/features/anuncio-wizard/components/wizard-ui'
import type { MonetizacaoCotacaoDuracao, MonetizacaoCotacaoOpcao } from '../types'

type SelectedMonetizacaoItem = {
  opcao: MonetizacaoCotacaoOpcao
  duracao: MonetizacaoCotacaoDuracao
}

function durationLabel(dias: number) {
  return dias === 1 ? '1 dia' : `${dias} dias`
}

export function MonetizacaoStepResumo({
  itens,
  saldoCreditos,
  totalCreditos,
}: {
  itens: SelectedMonetizacaoItem[]
  saldoCreditos: number
  totalCreditos: number
}) {
  const saldoApos = Math.max(0, saldoCreditos - totalCreditos)

  return (
    <StepPanel>
      <div className="mb-4 flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50">
          <ReceiptText className="h-5 w-5 text-zinc-500" />
        </div>
        <div>
          <h3 className="text-lg font-semibold text-zinc-950">Resumo da compra</h3>
          <p className="text-sm text-zinc-500">Confira os benefícios antes de seguir para o pagamento.</p>
        </div>
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
        <ReviewRow label="Subtotal" value={`${totalCreditos} créditos`} />
        <ReviewRow label="Créditos disponíveis" value={`${saldoCreditos} créditos`} />
        <ReviewRow label="Saldo após compra" value={`${saldoApos} créditos`} />
      </div>
    </StepPanel>
  )
}
