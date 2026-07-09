import Link from 'next/link'
import { ArrowRightIcon } from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'
import type { PainelRecomendacao } from '@/lib/painel-anunciante-api'

const PRIORITY_STYLES = {
  ALTA: 'border-pink-200 bg-pink-50 text-[#C41E73]',
  MEDIA: 'border-amber-200 bg-amber-50 text-amber-700',
  BAIXA: 'border-slate-200 bg-slate-50 text-slate-700',
} as const

export function RecommendationCard({ item }: { item: PainelRecomendacao }) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div>
          <span
            className={cn(
              'inline-flex rounded-full border px-2.5 py-1 text-[11px] font-bold uppercase tracking-[0.18em]',
              PRIORITY_STYLES[item.prioridade]
            )}
          >
            {item.prioridade}
          </span>
          <h3 className="mt-3 text-base font-bold text-slate-900">{item.titulo}</h3>
        </div>
      </div>

      <p className="mt-3 text-sm leading-6 text-slate-600">{item.descricao}</p>

      <Link
        href={item.ctaTarget}
        className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-[#FC1EAD] transition hover:text-[#d71897]"
      >
        {item.ctaLabel}
        <ArrowRightIcon className="h-4 w-4" />
      </Link>
    </div>
  )
}
