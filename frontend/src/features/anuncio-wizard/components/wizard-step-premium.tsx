'use client'

import { BadgeCheck, Check, Crown, ShieldCheck } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StepPanel } from './wizard-ui'

export function WizardStepPremium({
  premiumChoice,
  hasExistingKyc,
  onSelect,
  readOnly = false,
}: {
  premiumChoice: 'gratis' | 'destaque'
  hasExistingKyc: boolean
  onSelect: (choice: 'gratis' | 'destaque') => void
  readOnly?: boolean
}) {
  if (readOnly) {
    return (
      <StepPanel>
        <div className="rounded-[24px] border border-zinc-200 bg-zinc-50 px-5 py-5 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-white text-zinc-700 shadow-sm">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-zinc-950">Benefícios preservados</p>
              <p className="mt-1 text-sm leading-6 text-zinc-600">
                A edição não altera plano, créditos, destaque ou benefícios do anúncio.
              </p>
            </div>
          </div>
        </div>
      </StepPanel>
    )
  }

  return (
    <StepPanel>
      <div
        className={cn(
          'rounded-[24px] border px-5 py-4 shadow-sm',
          hasExistingKyc ? 'border-emerald-200 bg-emerald-50/80' : 'border-zinc-200 bg-zinc-50'
        )}
      >
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl',
              hasExistingKyc ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-600'
            )}
          >
            {hasExistingKyc ? (
              <BadgeCheck className="h-5 w-5" />
            ) : (
              <ShieldCheck className="h-5 w-5" />
            )}
          </div>
          <div className="space-y-1">
            <p className="text-sm font-semibold text-zinc-950">
              {hasExistingKyc
                ? 'Conta verificada. Seu anúncio será enviado para moderação.'
                : 'Vamos concluir rapidamente sua verificação para publicar seu anúncio.'}
            </p>
            <p className="text-sm leading-6 text-zinc-600">
              {hasExistingKyc
                ? 'Você pode seguir agora. O restante continua no fluxo normal de moderação.'
                : 'Se faltar documentação, o modal final vai pedir nome completo, CPF, data de nascimento e documentos antes do envio.'}
            </p>
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={() => onSelect('gratis')}
        className={cn(
          'flex w-full items-center justify-between rounded-2xl border p-4 text-left transition',
          premiumChoice === 'gratis'
            ? 'border-zinc-950 bg-zinc-950 text-white'
            : 'border-zinc-200 bg-white'
        )}
      >
        <span>
          <strong className="block">Publicar grátis</strong>
          <span
            className={cn(
              'text-sm',
              premiumChoice === 'gratis' ? 'text-zinc-200' : 'text-zinc-500'
            )}
          >
            Envio normal para moderação.
          </span>
        </span>
        {premiumChoice === 'gratis' && <Check className="h-5 w-5" />}
      </button>

      <button
        type="button"
        onClick={() => onSelect('destaque')}
        className={cn(
          'flex w-full items-center justify-between rounded-2xl border p-4 text-left transition',
          premiumChoice === 'destaque'
            ? 'border-rose-600 bg-rose-50'
            : 'border-zinc-200 bg-white'
        )}
      >
        <span>
          <strong className="block">Destacar meu anúncio</strong>
          <span className="text-sm text-zinc-500">
            Upgrade opcional. A ativação premium continua pelo fluxo existente.
          </span>
        </span>
        <Crown className="h-5 w-5 text-rose-600" />
      </button>
    </StepPanel>
  )
}
