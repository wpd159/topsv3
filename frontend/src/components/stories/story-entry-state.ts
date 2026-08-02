import type { MeuAnuncio } from '@/lib/meus-anuncios-api'

export type StoryEntryState =
  | {
      kind: 'ACTIVE'
      buttonLabel: 'Gerenciar Story'
      summary: string
      disabled: false
      reason: null
    }
  | {
      kind: 'READY'
      buttonLabel: 'Publicar nos Stories'
      summary: 'Disponível para publicar'
      disabled: false
      reason: null
    }
  | {
      kind: 'NEEDS_ACTIVATION'
      buttonLabel: 'Publicar nos Stories'
      summary: 'Ativar Stories'
      disabled: false
      reason: null
    }
  | {
      kind: 'UNAVAILABLE'
      buttonLabel: 'Publicar nos Stories'
      summary: string
      disabled: true
      reason: string
    }

const STORY_USABLE_STATUSES = new Set(['ATIVO', 'AGUARDANDO_MODERACAO'])

export function formatStoryDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'data indisponível'
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(date)
}

function unavailableReason(anuncio: MeuAnuncio) {
  if (anuncio.status === 'PAUSADO') return 'Reative o anúncio antes de usar os Stories.'
  if (anuncio.status === 'BLOQUEADO') return 'Desbloqueie o anúncio antes de usar os Stories.'
  if (anuncio.status === 'REMOVIDO') return 'Anúncios removidos não podem usar os Stories.'
  if (anuncio.status === 'REJEITADO' || anuncio.statusModeracao === 'REJEITADO') {
    return 'Corrija e reenvie o anúncio antes de usar os Stories.'
  }
  if (anuncio.status === 'RASCUNHO') return 'Publique o anúncio antes de usar os Stories.'
  if (anuncio.status === 'PENDENTE_REVISAO' || anuncio.statusModeracao === 'PENDENTE') {
    return 'Aguarde a aprovação do anúncio antes de usar os Stories.'
  }
  return 'Publique o anúncio antes de usar os Stories.'
}

export function getStoryEntryState(anuncio: MeuAnuncio): StoryEntryState {
  if (anuncio.storyAtivo) {
    return {
      kind: 'ACTIVE',
      buttonLabel: 'Gerenciar Story',
      summary: `Story ativo até ${formatStoryDate(anuncio.storyAtivo.fimEm)}`,
      disabled: false,
      reason: null,
    }
  }

  if (anuncio.status !== 'PUBLICADO' || anuncio.statusModeracao !== 'APROVADO') {
    const reason = unavailableReason(anuncio)
    return {
      kind: 'UNAVAILABLE',
      buttonLabel: 'Publicar nos Stories',
      summary: `Indisponível — ${reason.charAt(0).toLocaleLowerCase('pt-BR')}${reason.slice(1)}`,
      disabled: true,
      reason,
    }
  }

  const hasUsableBenefit = anuncio.beneficiosPremium.some(
    (benefit) => benefit.codigo === 'STORIES' && STORY_USABLE_STATUSES.has(benefit.status)
  )

  if (hasUsableBenefit) {
    return {
      kind: 'READY',
      buttonLabel: 'Publicar nos Stories',
      summary: 'Disponível para publicar',
      disabled: false,
      reason: null,
    }
  }

  return {
    kind: 'NEEDS_ACTIVATION',
    buttonLabel: 'Publicar nos Stories',
    summary: 'Ativar Stories',
    disabled: false,
    reason: null,
  }
}
