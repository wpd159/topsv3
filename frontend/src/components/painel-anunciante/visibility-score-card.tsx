import Link from 'next/link'
import { ArrowUpRightIcon, SparklesIcon } from '@heroicons/react/24/solid'
import type { PainelVisibilidade } from '@/lib/painel-anunciante-api'

function faixaStyles(faixa: string) {
  if (faixa.includes('alta')) return 'bg-emerald-100 text-emerald-700 border-emerald-200'
  if (faixa.includes('bom')) return 'bg-blue-100 text-blue-700 border-blue-200'
  if (faixa.includes('pode')) return 'bg-amber-100 text-amber-700 border-amber-200'
  return 'bg-rose-100 text-rose-700 border-rose-200'
}

export function VisibilityScoreCard({
  visibilidade,
  primaryCtaLabel,
  primaryCtaTarget,
}: {
  visibilidade: PainelVisibilidade
  primaryCtaLabel: string
  primaryCtaTarget: string
}) {
  const score = Math.max(0, Math.min(Number(visibilidade.score || 0), 100))

  return (
    <div className="rounded-[32px] border border-slate-200 bg-[linear-gradient(135deg,#fff_0%,#fff5fb_45%,#f8fbff_100%)] p-6 shadow-sm md:p-7">
      <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 rounded-full bg-white/90 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-[#FC1EAD] shadow-sm">
            <SparklesIcon className="h-4 w-4" />
            Score de visibilidade
          </div>

          <h2 className="mt-4 text-2xl font-extrabold tracking-tight text-slate-900 md:text-3xl">
            {visibilidade.anuncioTitulo}
          </h2>

          {visibilidade.localizacao ? (
            <p className="mt-2 text-sm font-medium text-slate-500">{visibilidade.localizacao}</p>
          ) : null}

          <p className="mt-4 max-w-xl text-sm leading-7 text-slate-600 md:text-base">
            {visibilidade.mensagem}
          </p>

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <span className={`rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] ${faixaStyles(visibilidade.faixa)}`}>
              {visibilidade.faixa}
            </span>
            <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
              {visibilidade.fotos} foto{visibilidade.fotos === 1 ? '' : 's'}
            </span>
            <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
              {visibilidade.recursosPremium} recurso{visibilidade.recursosPremium === 1 ? '' : 's'} premium
            </span>
          </div>
        </div>

        <div className="w-full max-w-sm rounded-3xl border border-white/80 bg-white/90 p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-slate-500">Pontuação atual</span>
            <span className="text-4xl font-extrabold tracking-tight text-slate-900">{score}</span>
          </div>

          <div className="mt-4 h-3 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-[linear-gradient(90deg,#FC1EAD_0%,#1d9bf0_100%)] transition-all"
              style={{ width: `${score}%` }}
            />
          </div>

          <p className="mt-4 text-sm leading-6 text-slate-600">
            Quanto mais perto de 100, maior a chance de o anúncio parecer competitivo, completo e comercialmente forte na vitrine.
          </p>

          <Link
            href={primaryCtaTarget}
            className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-[#FC1EAD] px-4 py-3 text-sm font-semibold text-white transition hover:bg-[#df1698]"
          >
            {primaryCtaLabel}
            <ArrowUpRightIcon className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </div>
  )
}
