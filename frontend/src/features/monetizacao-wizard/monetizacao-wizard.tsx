'use client'

import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { cn } from '@/lib/utils'
import { calculateAge } from '@/features/anuncio-wizard/wizard-utils'
import {
  ativarFeaturePorCatalogo,
  ativarImpulsionamento,
  fetchMonetizacaoWizardData,
} from './api'
import { MonetizacaoWizardPreview } from './components/monetizacao-wizard-preview'
import { MonetizacaoStepAnuncio } from './components/monetizacao-step-anuncio'
import { MonetizacaoStepBeneficios } from './components/monetizacao-step-beneficios'
import { MonetizacaoStepResumo } from './components/monetizacao-step-resumo'
import { MonetizacaoStepSaldo } from './components/monetizacao-step-saldo'
import { MonetizacaoStepSucesso } from './components/monetizacao-step-sucesso'
import type {
  MonetizacaoActivationResult,
  MonetizacaoCotacaoDuracao,
  MonetizacaoCotacaoOpcao,
  MonetizacaoOpcaoCodigo,
  MonetizacaoSelectionMode,
  MonetizacaoWizardData,
  MonetizacaoWizardStepId,
} from './types'

const steps: Array<{
  id: MonetizacaoWizardStepId
  eyebrow: string
  title: string
  description: string
}> = [
  {
    id: 'anuncio',
    eyebrow: 'Passo 1 de 5',
    title: 'Conferir anúncio',
    description: 'Revise os dados principais antes de escolher como monetizar.',
  },
  {
    id: 'beneficios',
    eyebrow: 'Passo 2 de 5',
    title: 'Escolha os benefícios',
    description: 'Escolha entre pacotes recomendados ou benefícios individuais.',
  },
  {
    id: 'resumo',
    eyebrow: 'Passo 3 de 5',
    title: 'Resumo da compra',
    description: 'Revise benefícios, subtotal, créditos disponíveis e validade antes de pagar.',
  },
  {
    id: 'pagamento',
    eyebrow: 'Passo 4 de 5',
    title: 'Pagamento',
    description: 'Confirme a ativação com créditos ou compre mais créditos para concluir.',
  },
  {
    id: 'sucesso',
    eyebrow: 'Passo 5 de 5',
    title: 'Confirmação',
    description: 'Veja o resumo da operação concluída.',
  },
]

const activationCodeMap: Record<string, string> = {
  FOTOS_EXTRA: 'FOTOS_EXTRA_5',
  VIDEO: 'VIDEO_1',
  WHATSAPP_DESTACADO: 'WHATSAPP_CARD',
  CARROSSEL: 'CARROSSEL_FOTOS',
  OCULTAR_IDADE: 'OCULTAR_IDADE',
  ANUNCIO_TOPO: 'ANUNCIO_TOPO',
}

function formatPrice(value: unknown) {
  if (typeof value === 'number') {
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
  }
  if (typeof value === 'string' && value.trim()) return value
  return 'Consulte valores'
}

function buildStatusSet(data: MonetizacaoWizardData | null) {
  const codes = new Set<string>()
  if (!data) return codes
  if (data.anuncio.impulsionado) codes.add('ANUNCIO_TOPO')
  for (const feature of data.anuncio.featuresAtivas || []) {
    if (feature.codigo) codes.add(feature.codigo)
  }
  return codes
}

function normalizeOptionComponents(option: MonetizacaoCotacaoOpcao | null) {
  if (!option) return [] as string[]
  if (option.codigo === 'PACOTE_RECOMENDADO') {
    return option.componentes
      .map((component) => activationCodeMap[component] || component)
      .filter((component) => component !== 'STORIES')
  }
  if (option.codigo === 'ANUNCIO_TOPO') return ['ANUNCIO_TOPO']
  if (option.codigo === 'STORIES') return []
  return [activationCodeMap[option.codigo] || option.codigo]
}

function hardBlockMessage(duracao: MonetizacaoCotacaoDuracao | null) {
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

function firstSelectableDuration(option: MonetizacaoCotacaoOpcao) {
  return option.duracoes.find((duration) => !hardBlockMessage(duration)) || null
}

function optionBelongsToMode(option: MonetizacaoCotacaoOpcao, mode: MonetizacaoSelectionMode) {
  if (mode === 'pacotes') return option.codigo === 'PACOTE_RECOMENDADO'
  return option.codigo !== 'PACOTE_RECOMENDADO' && option.codigo !== 'STORIES'
}

export default function MonetizacaoWizard({ slug }: { slug: string }) {
  const { usuario, carregando, refresh } = useAuth()
  const [data, setData] = useState<MonetizacaoWizardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [currentStep, setCurrentStep] = useState<MonetizacaoWizardStepId>('anuncio')
  const [selectionMode, setSelectionMode] = useState<MonetizacaoSelectionMode>('pacotes')
  const [openBenefit, setOpenBenefit] = useState<MonetizacaoOpcaoCodigo | null>(null)
  const [selectedDurations, setSelectedDurations] = useState<Partial<Record<MonetizacaoOpcaoCodigo, number>>>({})
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewHintDismissed, setPreviewHintDismissed] = useState(false)
  const [activationLoading, setActivationLoading] = useState(false)
  const [activationResult, setActivationResult] = useState<MonetizacaoActivationResult | null>(null)

  const loadWizard = async () => {
    setLoading(true)
    try {
      const nextData = await fetchMonetizacaoWizardData(slug)
      setData(nextData)
      return nextData
    } catch (error: any) {
      toast.error(error?.message || 'Não foi possível carregar o wizard de monetização.')
      return null
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!usuario) return
    void loadWizard()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slug, usuario?.id])

  const currentIndex = useMemo(
    () => Math.max(0, steps.findIndex((item) => item.id === currentStep)),
    [currentStep]
  )

  const opcoesVisiveis = useMemo(() => data?.cotacao.opcoes ?? [], [data?.cotacao.opcoes])

  useEffect(() => {
    const hasPackage = opcoesVisiveis.some((opcao) => opcao.codigo === 'PACOTE_RECOMENDADO')
    if (!hasPackage && selectionMode === 'pacotes') {
      setSelectionMode('individuais')
    }
  }, [opcoesVisiveis, selectionMode])

  const opcoesDoModo = useMemo(
    () => opcoesVisiveis.filter((opcao) => optionBelongsToMode(opcao, selectionMode)),
    [opcoesVisiveis, selectionMode]
  )

  const selectedItems = useMemo(() => {
    return Object.entries(selectedDurations)
      .map(([codigo, dias]) => {
        const opcao = opcoesDoModo.find((item) => item.codigo === codigo)
        const duracao = opcao?.duracoes.find((item) => item.dias === dias)
        return opcao && duracao ? { opcao, duracao } : null
      })
      .filter(Boolean) as Array<{ opcao: MonetizacaoCotacaoOpcao; duracao: MonetizacaoCotacaoDuracao }>
  }, [opcoesDoModo, selectedDurations])

  const totalCreditos = useMemo(
    () => selectedItems.reduce((total, item) => total + item.duracao.creditos, 0),
    [selectedItems]
  )

  const currentActiveCodes = useMemo(() => buildStatusSet(data), [data])
  const selectedEffectCodes = useMemo(() => {
    const codes = new Set<string>()
    selectedItems.forEach((item) => normalizeOptionComponents(item.opcao).forEach((code) => codes.add(code)))
    return Array.from(codes)
  }, [selectedItems])
  const idadeBase = calculateAge(usuario?.dataNascimento || null)

  const previewTitle =
    data?.anuncioEdit.titulo?.trim() || data?.anuncio.titulo?.trim() || 'Anúncio em destaque'
  const previewPrice = formatPrice(data?.anuncioEdit.preco || data?.anuncio.valor)
  const previewDescription =
    data?.anuncioEdit.descricao?.trim() || 'Seu anúncio aparece aqui com o mesmo card do site.'
  const previewImagesBase =
    data?.anuncioEdit.fotos?.filter(Boolean) || (data?.anuncio.fotoCapa ? [data.anuncio.fotoCapa] : [])
  const previewVideosBase = data?.anuncioEdit.videosAnuncio?.filter(Boolean) || []

  const beforeHasExtraPhotos = currentActiveCodes.has('FOTOS_EXTRA_5')
  const afterHasExtraPhotos = beforeHasExtraPhotos || selectedEffectCodes.includes('FOTOS_EXTRA_5')
  const beforeHasVideo = currentActiveCodes.has('VIDEO_1')
  const afterHasVideo = beforeHasVideo || selectedEffectCodes.includes('VIDEO_1')
  const beforeHasCarousel = currentActiveCodes.has('CARROSSEL_FOTOS')
  const afterHasCarousel = beforeHasCarousel || selectedEffectCodes.includes('CARROSSEL_FOTOS')
  const beforeHasWhatsapp = currentActiveCodes.has('WHATSAPP_CARD')
  const afterHasWhatsapp = beforeHasWhatsapp || selectedEffectCodes.includes('WHATSAPP_CARD')
  const beforeHasTop = currentActiveCodes.has('ANUNCIO_TOPO')
  const afterHasTop = beforeHasTop || selectedEffectCodes.includes('ANUNCIO_TOPO')
  const beforeHideAge = currentActiveCodes.has('OCULTAR_IDADE')
  const afterHideAge = beforeHideAge || selectedEffectCodes.includes('OCULTAR_IDADE')

  const beforePreview = useMemo(
    () => ({
      title: previewTitle,
      price: previewPrice,
      location: {
        estadoUf: data?.anuncioEdit.estadoUf ?? null,
        cidadeNome: data?.anuncioEdit.cidadeNome ?? null,
        bairroNome: data?.anuncioEdit.bairroNome ?? null,
        pontoReferenciaTexto: data?.anuncioEdit.pontoReferenciaTexto ?? null,
      },
      description: previewDescription,
      images: previewImagesBase.slice(0, beforeHasExtraPhotos ? 10 : 4),
      videos: beforeHasVideo ? previewVideosBase : [],
      idade: beforeHideAge ? null : idadeBase,
      destaque: beforeHasTop,
      carrosselDisponivel: beforeHasCarousel,
      videoHabilitado: beforeHasVideo,
      whatsappCardEnabled: beforeHasWhatsapp,
    }),
    [
      beforeHasCarousel,
      beforeHasExtraPhotos,
      beforeHasTop,
      beforeHasVideo,
      beforeHasWhatsapp,
      beforeHideAge,
      data?.anuncioEdit.bairroNome,
      data?.anuncioEdit.cidadeNome,
      data?.anuncioEdit.estadoUf,
      data?.anuncioEdit.pontoReferenciaTexto,
      idadeBase,
      previewDescription,
      previewImagesBase,
      previewPrice,
      previewTitle,
      previewVideosBase,
    ]
  )

  const afterPreview = useMemo(
    () => ({
      title: previewTitle,
      price: previewPrice,
      location: {
        estadoUf: data?.anuncioEdit.estadoUf ?? null,
        cidadeNome: data?.anuncioEdit.cidadeNome ?? null,
        bairroNome: data?.anuncioEdit.bairroNome ?? null,
        pontoReferenciaTexto: data?.anuncioEdit.pontoReferenciaTexto ?? null,
      },
      description: previewDescription,
      images: previewImagesBase.slice(0, afterHasExtraPhotos ? 10 : 4),
      videos: afterHasVideo ? previewVideosBase : [],
      idade: afterHideAge ? null : idadeBase,
      destaque: afterHasTop,
      carrosselDisponivel: afterHasCarousel,
      videoHabilitado: afterHasVideo,
      whatsappCardEnabled: afterHasWhatsapp,
    }),
    [
      afterHasCarousel,
      afterHasExtraPhotos,
      afterHasTop,
      afterHasVideo,
      afterHasWhatsapp,
      afterHideAge,
      data?.anuncioEdit.bairroNome,
      data?.anuncioEdit.cidadeNome,
      data?.anuncioEdit.estadoUf,
      data?.anuncioEdit.pontoReferenciaTexto,
      idadeBase,
      previewDescription,
      previewImagesBase,
      previewPrice,
      previewTitle,
      previewVideosBase,
    ]
  )

  const handleModeChange = (mode: MonetizacaoSelectionMode) => {
    setSelectionMode(mode)
    setOpenBenefit(null)
    setSelectedDurations({})
  }

  const toggleBenefit = (codigo: MonetizacaoOpcaoCodigo) => {
    const option = opcoesDoModo.find((item) => item.codigo === codigo)
    if (!option) return

    setSelectedDurations((current) => {
      if (current[codigo] != null) {
        const next = { ...current }
        delete next[codigo]
        return next
      }

      const firstDuration = firstSelectableDuration(option)
      if (!firstDuration) {
        const message = hardBlockMessage(option.duracoes[0] || null)
        toast.warning(message || 'Este benefício não está disponível agora.')
        return current
      }

      return { ...current, [codigo]: firstDuration.dias }
    })
  }

  const selectDuration = (codigo: MonetizacaoOpcaoCodigo, dias: number) => {
    const option = opcoesDoModo.find((item) => item.codigo === codigo)
    const duration = option?.duracoes.find((item) => item.dias === dias) || null
    const message = hardBlockMessage(duration)
    if (message) {
      toast.warning(message)
      return
    }

    setSelectedDurations((current) => ({ ...current, [codigo]: dias }))
  }

  const validateCurrentStep = () => {
    if (currentStep === 'beneficios' && selectedItems.length === 0) {
      toast.warning('Escolha pelo menos uma opção para continuar.')
      return false
    }

    return true
  }

  const goNext = () => {
    if (!validateCurrentStep()) return
    const next = steps[Math.min(currentIndex + 1, steps.length - 1)]
    setCurrentStep(next.id)
  }

  const goBack = () => {
    const previous = steps[Math.max(currentIndex - 1, 0)]
    setCurrentStep(previous.id)
  }

  const activateSelection = async () => {
    if (!data || selectedItems.length === 0) return

    if (data.cotacao.saldoCreditos < totalCreditos) {
      toast.warning('Seu saldo ainda não cobre esta compra.')
      return
    }

    setActivationLoading(true)
    const activatedCodes = new Set(currentActiveCodes)
    const activatedItems: MonetizacaoActivationResult['itens'] = []

    try {
      for (const item of selectedItems) {
        const components = normalizeOptionComponents(item.opcao)

        for (const code of components) {
          if (activatedCodes.has(code)) continue

          if (code === 'ANUNCIO_TOPO') {
            await ativarImpulsionamento(data.anuncio.id, item.duracao.dias)
          } else {
            await ativarFeaturePorCatalogo(data.anuncio.id, code, item.duracao.dias)
          }
          activatedCodes.add(code)
        }

        activatedItems.push({
          codigo: item.opcao.codigo,
          titulo: item.opcao.titulo,
          dias: item.duracao.dias,
          creditos: item.duracao.creditos,
        })
      }

      setActivationResult({
        itens: activatedItems,
        totalCreditos,
      })
      setSelectedDurations({})

      await refresh().catch(() => null)
      await loadWizard()
      setCurrentStep('sucesso')
      toast.success('Benefícios ativados com sucesso.')
    } catch (error: any) {
      const houveAtivacaoParcial = activatedCodes.size > currentActiveCodes.size
      if (houveAtivacaoParcial) {
        toast.error(
          'Alguns benefícios foram ativados, mas não foi possível concluir todos. Revise o anúncio antes de tentar novamente.'
        )
        return
      }
      toast.error(error?.message || 'Não foi possível concluir a ativação agora.')
    } finally {
      setActivationLoading(false)
    }
  }

  const currentStepCopy = steps[currentIndex]
  const showPreview = false

  const renderStep = () => {
    if (!data) return null

    switch (currentStep) {
      case 'anuncio':
        return <MonetizacaoStepAnuncio anuncio={data.anuncio} saldoCreditos={data.cotacao.saldoCreditos} />
      case 'beneficios':
        return (
          <MonetizacaoStepBeneficios
            opcoes={opcoesVisiveis}
            selectedDurations={selectedDurations}
            featuresAtivas={data.anuncio.featuresAtivas || []}
            dataFimImpulsionamento={data.anuncio.dataFimImpulsionamento}
            selectionMode={selectionMode}
            openCodigo={openBenefit}
            onModeChange={handleModeChange}
            onOpenChange={setOpenBenefit}
            onToggle={toggleBenefit}
            onSelectDuration={selectDuration}
          />
        )
      case 'resumo':
        return (
          <MonetizacaoStepResumo
            itens={selectedItems}
            saldoCreditos={data.cotacao.saldoCreditos}
            totalCreditos={totalCreditos}
          />
        )
      case 'pagamento':
        return (
          <MonetizacaoStepSaldo
            saldoCreditos={data.cotacao.saldoCreditos}
            totalCreditos={totalCreditos}
            loading={activationLoading}
            onActivate={() => void activateSelection()}
          />
        )
      case 'sucesso':
        return <MonetizacaoStepSucesso result={activationResult} />
      default:
        return null
    }
  }

  if (carregando || loading) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 text-sm text-zinc-500">
        Preparando o wizard de monetização...
      </div>
    )
  }

  if (!usuario) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">Acesso restrito</h1>
        <p className="mt-2 text-zinc-600">Faça login para monetizar seu anúncio.</p>
      </div>
    )
  }

  if (!data) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">Monetização indisponível</h1>
        <p className="mt-2 text-zinc-600">
          Não foi possível montar as opções comerciais deste anúncio agora.
        </p>
        <div className="mt-6">
          <Button type="button" onClick={() => void loadWizard()}>
            Tentar novamente
          </Button>
        </div>
      </div>
    )
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] text-zinc-950">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:px-8 lg:py-8">
        <section className="min-w-0 rounded-[30px] border border-zinc-200/90 bg-white shadow-[0_18px_60px_rgba(24,24,27,0.08)]">
          <div className="border-b border-zinc-100 px-5 py-5 sm:px-7">
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-[#FC1EAD]">{currentStepCopy.eyebrow}</p>
                <h1 className="mt-1 text-2xl font-semibold tracking-normal text-zinc-950 sm:text-3xl">
                  Monetizar anúncio
                </h1>
              </div>
            </div>

            <div
              className="mt-6 grid gap-1.5"
              style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}
            >
              {steps.map((step, idx) => (
                <button
                  key={step.id}
                  type="button"
                  onClick={() => {
                    if (idx <= currentIndex) setCurrentStep(step.id)
                  }}
                  className={cn(
                    'h-1.5 rounded-full transition-all duration-300',
                    idx < currentIndex
                      ? 'bg-zinc-950'
                      : idx === currentIndex
                        ? 'bg-gradient-to-r from-rose-500 via-fuchsia-500 to-violet-500 shadow-[0_0_0_1px_rgba(255,255,255,0.18)]'
                        : 'bg-zinc-200'
                  )}
                  aria-label={step.title}
                />
              ))}
            </div>

            {showPreview ? (
              <MonetizacaoWizardPreview
                showDesktop={false}
                renderDialog={false}
                open={previewOpen}
                onOpenChange={setPreviewOpen}
                onOpenRequest={() => {
                  setPreviewHintDismissed(true)
                  setPreviewOpen(true)
                }}
                highlightedPreview
                previewHintActive={!previewHintDismissed}
                title={previewTitle}
                before={beforePreview}
                after={afterPreview}
              />
            ) : null}
          </div>

          <div className="px-5 py-6 sm:px-7 sm:py-8">
            <div className="mb-7 space-y-2">
              <p className="text-sm font-medium text-rose-700">{currentStepCopy.eyebrow}</p>
              <h2 className="text-[28px] font-semibold leading-tight tracking-normal text-zinc-950">
                {currentStepCopy.title}
              </h2>
              <p className="max-w-2xl text-sm leading-6 text-zinc-600">
                {currentStepCopy.description}
              </p>
            </div>

            {renderStep()}

            {currentStep !== 'sucesso' ? (
              <div className="mt-8 flex items-center justify-between gap-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={goBack}
                  disabled={currentIndex === 0 || activationLoading}
                >
                  <ChevronLeft className="mr-2 h-4 w-4" />
                  Voltar
                </Button>

                {currentStep === 'pagamento' ? null : (
                  <Button type="button" onClick={goNext}>
                    Continuar
                    <ChevronRight className="ml-2 h-4 w-4" />
                  </Button>
                )}
              </div>
            ) : null}
          </div>
        </section>

        {showPreview ? (
          <MonetizacaoWizardPreview
            showMobile={false}
            open={previewOpen}
            onOpenChange={setPreviewOpen}
            onOpenRequest={() => {
              setPreviewHintDismissed(true)
              setPreviewOpen(true)
            }}
            highlightedPreview
            previewHintActive={!previewHintDismissed}
            title={previewTitle}
            before={beforePreview}
            after={afterPreview}
          />
        ) : null}
      </div>
    </main>
  )
}
