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
  invalidField = null,
}: {
  state: WizardFormState
  onToggle: (field: 'locaisAtendimento' | 'servicos', value: string) => void
  onPatch: (payload: Partial<WizardFormState>) => void
  mode?: 'create' | 'edit'
  invalidField?: string | null
}) {
  return (
    <StepPanel>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Preço" invalid={invalidField === 'preco'}>
          <Input
            aria-invalid={invalidField === 'preco'}
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
        invalid={invalidField === 'locaisAtendimento'}
        items={locais}
        selected={state.locaisAtendimento}
        onToggle={(value) => onToggle('locaisAtendimento', value)}
      />

      <ChoiceGroup
        title="Serviços"
        invalid={invalidField === 'servicos'}
        items={servicos}
        selected={state.servicos}
        onToggle={(value) => onToggle('servicos', value)}
      />

      {state.servicos.includes('VIDEOCHAMADA') ? (
        <label className={`flex items-start gap-3 rounded-lg border bg-zinc-50 p-4 ${invalidField === 'atendimentoExclusivamenteVirtual' ? 'border-red-600 ring-2 ring-red-600' : 'border-zinc-200'}`}>
          <Checkbox
            aria-invalid={invalidField === 'atendimentoExclusivamenteVirtual'}
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

      {mode === 'edit' ? <Field label="Conte um pouco mais sobre a experiência" invalid={invalidField === 'descricao'}>
        <AutoResizeTextarea
          invalid={invalidField === 'descricao'}
          value={state.descricao}
          onChange={(event) => onPatch({ descricao: event.target.value })}
          placeholder="Conte o que faz seu atendimento ser especial, o clima que você gosta de transmitir e o que as pessoas podem esperar."
          minRows={4}
          maxRows={7}
          maxLength={500}
        />
      </Field> : null}

      <div className="space-y-2">
        <Field label="Link para seu conteúdo" invalid={invalidField === 'linkConteudo'}>
          <Input
            aria-invalid={invalidField === 'linkConteudo'}
            type="url"
            value={state.linkConteudo}
            onChange={(event) => onPatch({ linkConteudo: event.target.value })}
            placeholder="https://seulink.com"
            inputMode="url"
          />
        </Field>
        <p className="text-sm leading-6 text-zinc-600">
          Vende conteúdo? Adicione aqui o link da sua página, plataforma ou catálogo.
        </p>
      </div>
    </StepPanel>
  )
}
