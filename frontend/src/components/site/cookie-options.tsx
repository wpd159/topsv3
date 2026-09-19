'use client'

import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import type { ConsentState } from '@/lib/cookie-consent'

export function CookieOptions({
  consent,
  onChange,
  disabled = false,
}: {
  consent: ConsentState
  onChange: (consent: ConsentState) => void
  disabled?: boolean
}) {
  return (
    <div className="space-y-5 text-left">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="font-medium">Necessarios</p>
          <p className="text-sm text-gray-600">
            Sempre ativos para seguranca, login e integridade do site.
          </p>
        </div>
        <Badge variant="secondary" className="bg-gray-100">Sempre ativo</Badge>
      </div>
      <Separator />
      {([
        ['functional', 'Funcionais', 'Lembrar idioma e preferencias de interface.'],
        ['analytics', 'Analytics', 'Metricas agregadas para evoluir o produto.'],
        ['marketing', 'Marketing', 'Mensuracao e relevancia de campanhas.'],
      ] as const).map(([key, label, description]) => (
        <div key={key} className="flex items-start justify-between gap-6">
          <div>
            <p className="font-medium">{label}</p>
            <p className="text-sm text-gray-600">{description}</p>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={consent[key]}
            aria-label={label}
            disabled={disabled}
            onClick={() => onChange({ ...consent, [key]: !consent[key] })}
            className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
              consent[key] ? 'bg-[#FC1EAD]' : 'bg-gray-300'
            }`}
          >
            <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
              consent[key] ? 'translate-x-5' : 'translate-x-1'
            }`} />
          </button>
        </div>
      ))}
    </div>
  )
}
