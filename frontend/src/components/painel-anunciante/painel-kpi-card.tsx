import { cn } from '@/lib/utils'

type PainelKpiCardProps = {
  label: string
  value: string | number
  helper: string
  accent?: 'pink' | 'blue' | 'green' | 'amber'
}

const ACCENT_MAP = {
  pink: 'from-pink-100 via-pink-50 to-white text-[#FC1EAD]',
  blue: 'from-blue-100 via-blue-50 to-white text-blue-700',
  green: 'from-emerald-100 via-emerald-50 to-white text-emerald-700',
  amber: 'from-amber-100 via-amber-50 to-white text-amber-700',
} as const

export function PainelKpiCard({
  label,
  value,
  helper,
  accent = 'pink',
}: PainelKpiCardProps) {
  return (
    <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
      <div
        className={cn(
          'border-b bg-gradient-to-r px-5 py-3 text-xs font-semibold uppercase tracking-[0.18em]',
          ACCENT_MAP[accent]
        )}
      >
        {label}
      </div>
      <div className="space-y-2 px-5 py-5">
        <p className="text-3xl font-extrabold tracking-tight text-slate-900">{value}</p>
        <p className="text-sm leading-6 text-slate-600">{helper}</p>
      </div>
    </div>
  )
}
