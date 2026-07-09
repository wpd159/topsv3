'use client'

import { cn } from '@/lib/utils'
import type { MonetizacaoCotacaoDuracao } from '../types'

function durationLabel(dias: number) {
  return dias === 1 ? '1 dia' : `${dias} dias`
}

function durationStatusLabel(duracao: MonetizacaoCotacaoDuracao) {
  if (duracao.motivoBloqueio === 'ANUNCIO_NAO_ATIVO') {
    return 'Os benefícios premium podem ser adquiridos após a aprovação e ativação do anúncio.'
  }
  if (duracao.motivoBloqueio === 'BENEFICIO_JA_ATIVO') {
    return 'Este benefício já está ativo neste anúncio.'
  }
  if (duracao.motivoBloqueio && duracao.motivoBloqueio !== 'CREDITOS_INSUFICIENTES') {
    return 'Esta opção não está disponível agora.'
  }
  if (duracao.saldoSuficiente && duracao.podeAtivar) {
    return 'Seu saldo atual já cobre esta duração.'
  }
  return `Faltam ${duracao.creditosFaltantes} créditos para ativar.`
}

export function MonetizacaoStepDuracao({
  duracoes,
  selectedDias,
  onSelect,
}: {
  duracoes: MonetizacaoCotacaoDuracao[]
  selectedDias: number | null
  onSelect: (dias: number) => void
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {duracoes.map((duracao) => {
        const selected = selectedDias === duracao.dias
        return (
          <button
            key={duracao.dias}
            type="button"
            onClick={() => onSelect(duracao.dias)}
            className={cn(
              'rounded-[28px] border px-5 py-5 text-left transition-all duration-300',
              selected
                ? 'border-zinc-950 bg-zinc-950 text-white shadow-[0_18px_36px_rgba(24,24,27,0.16)]'
                : 'border-zinc-200 bg-white hover:border-zinc-300 hover:shadow-[0_16px_32px_rgba(24,24,27,0.08)]'
            )}
          >
            <p className={cn('text-[11px] font-semibold uppercase tracking-[0.16em]', selected ? 'text-zinc-300' : 'text-zinc-500')}>
              Duração
            </p>
            <h3 className="mt-3 text-2xl font-semibold tracking-tight">{durationLabel(duracao.dias)}</h3>
            <p className={cn('mt-4 text-sm', selected ? 'text-zinc-200' : 'text-zinc-600')}>
              {duracao.creditos} créditos
            </p>
            <div className={cn('mt-4 rounded-2xl px-4 py-3 text-sm', selected ? 'bg-white/10 text-zinc-100' : 'bg-zinc-50 text-zinc-700')}>
              {durationStatusLabel(duracao)}
            </div>
          </button>
        )
      })}
    </div>
  )
}
