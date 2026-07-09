'use client'

import type { ComponentType } from 'react'
import {
  ChevronDown,
  Crown,
  Film,
  ImageIcon,
  Layers3,
  MessageCircleMore,
  ShieldOff,
  Stars,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type {
  FeatureAtivaResumo,
  MonetizacaoCotacaoDuracao,
  MonetizacaoCotacaoOpcao,
  MonetizacaoOpcaoCodigo,
  MonetizacaoSelectionMode,
} from '../types'

const ICONS: Partial<Record<MonetizacaoOpcaoCodigo, ComponentType<{ className?: string }>>> = {
  ANUNCIO_TOPO: Crown,
  FOTOS_EXTRA: ImageIcon,
  VIDEO: Film,
  WHATSAPP_DESTACADO: MessageCircleMore,
  CARROSSEL: Layers3,
  OCULTAR_IDADE: ShieldOff,
  PACOTE_RECOMENDADO: Stars,
}

const IMPACT_LABELS: Partial<Record<MonetizacaoOpcaoCodigo, string>> = {
  ANUNCIO_TOPO: 'Apareça acima dos demais anúncios e receba mais visualizações.',
  FOTOS_EXTRA: 'Mostre mais fotos e deixe seu perfil mais completo.',
  VIDEO: 'Inclua vídeo para gerar mais confiança no primeiro contato.',
  WHATSAPP_DESTACADO: 'Deixe o botão de contato mais visível no card.',
  CARROSSEL: 'Valorize as fotos com navegação em carrossel.',
  OCULTAR_IDADE: 'Controle a exibição pública da sua idade.',
  PACOTE_RECOMENDADO: 'Combine os principais recursos de visibilidade.',
}

function durationLabel(dias: number) {
  return dias === 1 ? '1 dia' : `${dias} dias`
}

function formatDate(value?: unknown) {
  if (!value) return null
  const date = Array.isArray(value)
    ? new Date(
        Number(value[0]),
        Number(value[1] || 1) - 1,
        Number(value[2] || 1),
        Number(value[3] || 0),
        Number(value[4] || 0),
        Number(value[5] || 0)
      )
    : new Date(String(value))

  if (Number.isNaN(date.getTime())) return null
  return new Intl.DateTimeFormat('pt-BR').format(date)
}

function hardBlockReason(duracao?: MonetizacaoCotacaoDuracao | null) {
  const reason = duracao?.motivoBloqueio
  if (!reason || reason === 'CREDITOS_INSUFICIENTES') return null

  if (reason === 'ANUNCIO_NAO_ATIVO') {
    return 'Os benefícios premium podem ser adquiridos após a aprovação e ativação do anúncio.'
  }
  if (reason === 'BENEFICIO_JA_ATIVO') {
    return 'Este benefício já está ativo neste anúncio.'
  }
  if (reason === 'CATALOGO_INATIVO') {
    return 'Este benefício está indisponível no momento.'
  }
  if (reason === 'DURACOES_NAO_CONFIGURADAS' || reason === 'DURACAO_TOPO_NAO_CONFIGURADA') {
    return 'Esta duração ainda não está configurada.'
  }

  return 'Este benefício não pode ser ativado agora.'
}

function activeUntilForOption(
  opcao: MonetizacaoCotacaoOpcao,
  featuresAtivas: FeatureAtivaResumo[],
  dataFimImpulsionamento?: string | null
) {
  if (opcao.codigo === 'ANUNCIO_TOPO') return formatDate(dataFimImpulsionamento)

  const map: Partial<Record<MonetizacaoOpcaoCodigo, string>> = {
    FOTOS_EXTRA: 'FOTOS_EXTRA_5',
    VIDEO: 'VIDEO_1',
    WHATSAPP_DESTACADO: 'WHATSAPP_CARD',
    CARROSSEL: 'CARROSSEL_FOTOS',
    OCULTAR_IDADE: 'OCULTAR_IDADE',
  }
  const active = featuresAtivas.find((feature) => feature.codigo === map[opcao.codigo])
  return formatDate(active?.expiraEm)
}

export function MonetizacaoStepBeneficios({
  opcoes,
  selectedDurations,
  featuresAtivas,
  dataFimImpulsionamento,
  selectionMode,
  openCodigo,
  onModeChange,
  onOpenChange,
  onToggle,
  onSelectDuration,
}: {
  opcoes: MonetizacaoCotacaoOpcao[]
  selectedDurations: Partial<Record<MonetizacaoOpcaoCodigo, number>>
  featuresAtivas: FeatureAtivaResumo[]
  dataFimImpulsionamento?: string | null
  selectionMode: MonetizacaoSelectionMode
  openCodigo: MonetizacaoOpcaoCodigo | null
  onModeChange: (mode: MonetizacaoSelectionMode) => void
  onOpenChange: (codigo: MonetizacaoOpcaoCodigo | null) => void
  onToggle: (codigo: MonetizacaoOpcaoCodigo) => void
  onSelectDuration: (codigo: MonetizacaoOpcaoCodigo, dias: number) => void
}) {
  const packageOptions = opcoes.filter((opcao) => opcao.codigo === 'PACOTE_RECOMENDADO')
  const individualOptions = opcoes.filter(
    (opcao) => opcao.codigo !== 'PACOTE_RECOMENDADO' && opcao.codigo !== 'STORIES'
  )
  const visibleOptions = selectionMode === 'pacotes' ? packageOptions : individualOptions

  return (
    <div className="space-y-5">
      <section className="rounded-[28px] border border-zinc-200 bg-white p-4 shadow-sm sm:p-5">
        <p className="text-sm font-semibold text-zinc-950">Como deseja monetizar?</p>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          {[
            { id: 'pacotes' as const, title: 'Pacotes recomendados', text: 'Combinações prontas para ganhar visibilidade.' },
            { id: 'individuais' as const, title: 'Benefícios individuais', text: 'Escolha apenas os recursos que deseja ativar.' },
          ].map((mode) => (
            <button
              key={mode.id}
              type="button"
              onClick={() => onModeChange(mode.id)}
              className={cn(
                'rounded-[22px] border px-4 py-4 text-left transition',
                selectionMode === mode.id
                  ? 'border-[#FC1EAD] bg-[#fff0f8] shadow-[0_12px_28px_rgba(252,30,173,0.08)]'
                  : 'border-zinc-200 bg-white hover:border-zinc-300'
              )}
            >
              <div className="flex items-center gap-3">
                <span
                  className={cn(
                    'flex h-5 w-5 items-center justify-center rounded-full border',
                    selectionMode === mode.id ? 'border-[#FC1EAD]' : 'border-zinc-300'
                  )}
                >
                  {selectionMode === mode.id ? <span className="h-2.5 w-2.5 rounded-full bg-[#FC1EAD]" /> : null}
                </span>
                <span className="text-sm font-semibold text-zinc-950">{mode.title}</span>
              </div>
              <p className="mt-2 text-sm leading-6 text-zinc-600">{mode.text}</p>
            </button>
          ))}
        </div>
      </section>

      <section className="space-y-3">
        {visibleOptions.length === 0 ? (
          <div className="rounded-[28px] border border-dashed border-zinc-300 bg-white px-5 py-8 text-center text-sm text-zinc-500">
            Nenhuma opção disponível nesta categoria agora.
          </div>
        ) : null}

        {visibleOptions.map((opcao) => {
          const Icon = ICONS[opcao.codigo] || Stars
          const selectedDias = selectedDurations[opcao.codigo]
          const selected = selectedDias != null
          const selectedDuration =
            opcao.duracoes.find((duracao) => duracao.dias === selectedDias) || opcao.duracoes[0] || null
          const activeUntil = activeUntilForOption(opcao, featuresAtivas, dataFimImpulsionamento)
          const displayBlockMessage = hardBlockReason(selectedDuration)
          const hasSelectableDuration = opcao.duracoes.some((duracao) => !hardBlockReason(duracao))
          const available = opcao.disponivel && opcao.duracoes.length > 0 && hasSelectableDuration
          const open = openCodigo === opcao.codigo

          return (
            <div
              key={opcao.codigo}
              className={cn(
                'overflow-hidden rounded-[24px] border transition-all duration-300',
                selected
                  ? 'border-[#FC1EAD] bg-[#fff0f8] shadow-[0_12px_28px_rgba(252,30,173,0.08)]'
                  : 'border-zinc-200 bg-white',
                !available && 'opacity-75'
              )}
            >
              <button
                type="button"
                onClick={() => onOpenChange(open ? null : opcao.codigo)}
                className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left sm:px-5 sm:py-4"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <div
                    className={cn(
                      'flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl border sm:h-11 sm:w-11',
                      selected
                        ? 'border-[#FC1EAD]/30 bg-white text-[#FC1EAD]'
                        : 'border-zinc-200 bg-zinc-50 text-zinc-700'
                    )}
                  >
                    <Icon className="h-5 w-5" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-base font-semibold text-zinc-950">{opcao.titulo}</h3>
                      {selected ? (
                        <span className="rounded-full border border-[#FC1EAD]/25 bg-white px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-[#C51683]">
                          Selecionado
                        </span>
                      ) : activeUntil ? (
                        <span className="rounded-full border border-zinc-200 bg-zinc-50 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-zinc-600">
                          Já ativo
                        </span>
                      ) : null}
                    </div>
                    <p className="mt-1 text-sm leading-5 text-zinc-600 sm:leading-6">
                      {activeUntil
                        ? `Já ativo até ${activeUntil}`
                        : displayBlockMessage || opcao.descricao || IMPACT_LABELS[opcao.codigo] || 'Escolha a duração para ativar este recurso.'}
                    </p>
                  </div>
                </div>
                <ChevronDown
                  className={cn('h-5 w-5 shrink-0 text-zinc-500 transition-transform', open && 'rotate-180')}
                />
              </button>

              {open ? (
                <div className="border-t border-zinc-200/80 px-4 pb-4 pt-3 sm:px-5">
                  <div className="grid gap-2">
                    {opcao.duracoes.map((duracao) => {
                      const durationBlocked = Boolean(hardBlockReason(duracao))
                      const durationSelected = selectedDias === duracao.dias
                      return (
                        <button
                          key={duracao.dias}
                          type="button"
                          disabled={durationBlocked}
                          onClick={() => {
                            onSelectDuration(opcao.codigo, duracao.dias)
                          }}
                          className={cn(
                            'flex items-center justify-between rounded-2xl border px-3 py-2 text-sm transition',
                            durationSelected
                              ? 'border-[#FC1EAD] bg-[#FC1EAD] text-white shadow-sm'
                              : 'border-zinc-200 bg-white text-zinc-700 hover:border-[#FC1EAD]/40',
                            durationBlocked && 'cursor-not-allowed opacity-60'
                          )}
                        >
                          <span>{durationLabel(duracao.dias)}</span>
                          <span className="font-semibold">{duracao.creditos} créditos</span>
                        </button>
                      )
                    })}
                  </div>

                  {!selected ? (
                    <button
                      type="button"
                      disabled={!available}
                      onClick={() => onToggle(opcao.codigo)}
                      className="mt-3 h-10 w-full rounded-2xl border border-zinc-200 bg-white text-sm font-semibold text-zinc-800 transition hover:border-[#FC1EAD]/40 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      Selecionar benefício
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => onToggle(opcao.codigo)}
                      className="mt-3 h-10 w-full rounded-2xl border border-[#FC1EAD]/30 bg-white text-sm font-semibold text-[#C51683] transition hover:border-[#FC1EAD] hover:bg-[#fff0f8]"
                    >
                      Remover benefício
                    </button>
                  )}
                </div>
              ) : null}
            </div>
          )
        })}
      </section>
    </div>
  )
}
