'use client'

import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import type { WizardFormState } from '../types'
import { formatCurrencyBRL } from '@/utils/formatter'
import { horarios, locais, servicos } from '../wizard-constants'
import { selectClassName } from '../wizard-utils'
import { AutoResizeTextarea, ChoiceGroup, Field, StepPanel } from './wizard-ui'

export function WizardStepServicos({
  state,
  onToggle,
  onPatch,
  mode = 'create',
}: {
  state: WizardFormState
  onToggle: (field: 'locaisAtendimento' | 'servicos', value: string) => void
  onPatch: (payload: Partial<WizardFormState>) => void
  mode?: 'create' | 'edit'
}) {
  return (
    <StepPanel>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Preço">
          <Input
            value={state.preco}
            onChange={(event) => {
              const digits = event.target.value.replace(/\D/g, '').slice(0, 8)
              const formatted = digits ? formatCurrencyBRL(Number(digits) / 100) : ''
              onPatch({ preco: formatted })
            }}
            placeholder="R$ 0,00"
          />
        </Field>

        {mode === 'create' ? <Field label="Horário">
          <select
            className={selectClassName()}
            value={state.horario}
            onChange={(event) => onPatch({ horario: event.target.value })}
          >
            <option value="">Selecione</option>
            {horarios.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </Field> : null}
      </div>

      <ChoiceGroup
        title="Local de atendimento"
        items={locais}
        selected={state.locaisAtendimento}
        onToggle={(value) => onToggle('locaisAtendimento', value)}
      />

      <ChoiceGroup
        title="Serviços"
        items={servicos}
        selected={state.servicos}
        onToggle={(value) => onToggle('servicos', value)}
      />

      {state.servicos.includes('VIDEOCHAMADA') ? (
        <label className="flex items-start gap-3 rounded-lg border border-zinc-200 bg-zinc-50 p-4">
          <Checkbox
            checked={state.atendimentoExclusivamenteVirtual}
            onCheckedChange={(checked) => onPatch({
              atendimentoExclusivamenteVirtual: checked === true,
            })}
            aria-label="Atendimento exclusivamente virtual"
          />
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-zinc-900">
              Atendimento exclusivamente virtual
            </span>
            <span className="mt-1 block text-sm text-zinc-600">
              Marque apenas se você não realiza atendimento presencial
            </span>
          </span>
        </label>
      ) : null}

      <Field label="Conte um pouco mais sobre a experiência">
        <AutoResizeTextarea
          value={state.descricao}
          onChange={(event) => onPatch({ descricao: event.target.value })}
          placeholder="Conte o que faz seu atendimento ser especial, o clima que você gosta de transmitir e o que as pessoas podem esperar."
          minRows={4}
          maxRows={7}
          maxLength={2000}
        />
      </Field>

      {mode === 'edit' ? <Field label="WhatsApp">
        <Input
          type="tel"
          value={state.whatsapp}
          onChange={(event) => onPatch({ whatsapp: event.target.value })}
          placeholder="+55 62 99999-9999"
          autoComplete="tel"
        />
      </Field> : null}

      {mode === 'create' ? <Field label="Link de conteúdo">
        <Input
          value={state.linkConteudo}
          onChange={(event) => onPatch({ linkConteudo: event.target.value })}
          placeholder="https://"
        />
      </Field> : null}
    </StepPanel>
  )
}
