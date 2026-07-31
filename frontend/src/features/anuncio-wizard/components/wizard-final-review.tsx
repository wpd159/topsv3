'use client'

import type { WizardFormState } from '../types'
import {
  formatWizardPhotoCount,
  formatWizardSchedule,
  formatWizardServices,
} from '../wizard-utils'
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
      <ReviewRow label="Seu anúncio" value={`${previewTitle} · ${categoriaLabel}`} />
      <ReviewRow
        label="Onde você atende"
        value={[previewLocation, previewReference].filter(Boolean).join(' · ') || 'Não informado'}
      />
      <ReviewRow
        label="Atendimento"
        value={[formatWizardSchedule(state.horario), state.preco].filter(Boolean).join(' · ')}
      />
      <ReviewRow
        label="Serviços oferecidos"
        value={formatWizardServices(state.servicos)}
      />
      <ReviewRow label="Fotos" value={formatWizardPhotoCount(totalFotos)} />
      <ReviewRow
        label="Atendimento Virtual"
        value={
          hasVirtual
            ? state.atendimentoExclusivamenteVirtual
              ? 'Atendimento exclusivamente virtual'
              : 'Atendimento presencial e virtual'
            : 'Não informado'
        }
      />
    </StepPanel>
  )
}
