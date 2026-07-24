'use client'

import type { WizardFormState } from '../types'
import { ReviewRow, StepPanel } from './wizard-ui'

export function WizardFinalReview({
  previewTitle,
  categoriaLabel,
  previewLocation,
  previewReference,
  state,
  hasVirtual,
  photoCount,
}: {
  previewTitle: string
  categoriaLabel: string
  previewLocation: string
  previewReference: string
  state: WizardFormState
  hasVirtual: boolean
  photoCount?: number
}) {
  const totalFotos = photoCount ?? state.fotos.length

  return (
    <StepPanel>
      <ReviewRow label="Anúncio" value={`${previewTitle} · ${categoriaLabel}`} />
      <ReviewRow
        label="Localização"
        value={`${previewLocation}${previewReference ? ` · ${previewReference}` : ''}`}
      />
      <ReviewRow
        label="Atendimento"
        value={[state.horario, state.preco].filter(Boolean).join(' · ') || 'Pendente'}
      />
      <ReviewRow
        label="Serviços"
        value={state.servicos.length ? state.servicos.join(', ') : 'Pendente'}
      />
      <ReviewRow label="Fotos" value={`${totalFotos} selecionada(s)`} />
      <ReviewRow
        label="Sexo Virtual"
        value={
          hasVirtual
            ? state.atendimentoExclusivamenteVirtual
              ? 'Atendimento exclusivamente virtual'
              : 'Atendimento presencial e virtual'
            : 'Não selecionado'
        }
      />
    </StepPanel>
  )
}
