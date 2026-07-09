'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthContext'
import { useLocalidades } from '@/hooks/useLocalidades'
import { useAnuncioEdit } from '@/hooks/useAnuncioEdit'
import { cn } from '@/lib/utils'
import { categorias, stepCopy, type SearchableSelectOption } from './wizard-constants'
import { calculateAge, hasPersistedKyc } from './wizard-utils'
import { updateWizardProfileDescription } from './api'
import { WizardFinalReview } from './components/wizard-final-review'
import { WizardPreview } from './components/wizard-preview'
import { WizardStepFotos } from './components/wizard-step-fotos'
import { WizardStepLocalizacao } from './components/wizard-step-localizacao'
import { WizardStepPerfil } from './components/wizard-step-perfil'
import { WizardStepServicos } from './components/wizard-step-servicos'
import {
  createWizardProgressSessionId,
  syncWizardProgress,
  type WizardProgressStatus,
  type WizardProgressStep,
} from './wizard-progress'

const editWizardSteps = [
  { id: 'perfil', title: 'Perfil do anúncio', eyebrow: 'Comece pelo essencial' },
  { id: 'localizacao', title: 'Área de atendimento', eyebrow: 'Localização contextual' },
  { id: 'servicos', title: 'Atendimento', eyebrow: 'Serviços e experiência' },
  { id: 'fotos', title: 'Fotos', eyebrow: 'O anúncio ganha forma' },
  { id: 'revisao', title: 'Revise e salve', eyebrow: 'Última checagem' },
] as const

type EditWizardStepId = (typeof editWizardSteps)[number]['id']

function stepIndexFromId(step: EditWizardStepId) {
  return Math.max(0, editWizardSteps.findIndex((item) => item.id === step))
}

export default function EditarAnuncioWizard({ slug }: { slug: string }) {
  const router = useRouter()
  const { usuario, carregando: carregandoUsuario } = useAuth()
  const API = process.env.NEXT_PUBLIC_API_URL || ''
  const localidades = useLocalidades(API)
  const [currentStep, setCurrentStep] = useState<EditWizardStepId>('perfil')
  const [previewOpen, setPreviewOpen] = useState(false)
  const [previewHintDismissed, setPreviewHintDismissed] = useState(false)
  const [fotoPreviewUrls, setFotoPreviewUrls] = useState<string[]>([])
  const [descricaoPerfil, setDescricaoPerfil] = useState('')
  const wizardTopRef = useRef<HTMLDivElement | null>(null)
  const stepDidMountRef = useRef(false)
  const wizardSessionIdRef = useRef(createWizardProgressSessionId())
  const lastSyncedStepRef = useRef<WizardProgressStep | null>(null)

  const anuncio = useAnuncioEdit({
    API,
    slug,
    usuario,
    defaultMaxFotos: 15,
    maxMB: 20,
    locaisApi: {
      loadCidades: localidades.loadCidades,
      loadBairros: localidades.loadBairros,
      setCidades: localidades.setCidades,
      setBairros: localidades.setBairros,
    },
    onSuccess: () => router.push('/meus-anuncios'),
  })

  const currentIndex = stepIndexFromId(currentStep)
  const idade = calculateAge((usuario as any)?.dataNascimento)
  const hasExistingKyc = hasPersistedKyc(usuario)
  const persistedProfileDescription = String((usuario as any)?.descricao || '').trim()
  const photoCount = anuncio.fotosExistentes.length + anuncio.fotosNovas.length

  const syncProgress = (
    ultimoStep: WizardProgressStep,
    status: WizardProgressStatus = 'EM_PREENCHIMENTO',
    anuncioId?: number | string | null
  ) => {
    lastSyncedStepRef.current = ultimoStep
    return syncWizardProgress({
      sessionId: wizardSessionIdRef.current,
      mode: 'edit',
      ultimoStep,
      status,
      anuncioId,
    })
  }

  useEffect(() => {
    if (descricaoPerfil.trim().length > 0) return
    if (!persistedProfileDescription) return
    setDescricaoPerfil(persistedProfileDescription)
  }, [descricaoPerfil, persistedProfileDescription])

  useEffect(() => {
    const urls = anuncio.fotosNovas.map((file) => URL.createObjectURL(file))
    setFotoPreviewUrls(urls)
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [anuncio.fotosNovas])

  useEffect(() => {
    if (!stepDidMountRef.current) {
      stepDidMountRef.current = true
      return
    }

    window.requestAnimationFrame(() => {
      wizardTopRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }, [currentIndex])

  useEffect(() => {
    if (anuncio.carregando) return
    if (!usuario) return
    const stepId = currentStep as WizardProgressStep
    if (lastSyncedStepRef.current === stepId) return
    syncProgress(stepId, 'EM_PREENCHIMENTO', anuncio.anuncioId)
  }, [currentStep, anuncio.carregando, anuncio.anuncioId, usuario])

  const previewTitle = anuncio.titulo.trim() || 'Seu nome em destaque'
  const profileDescription = descricaoPerfil.trim()
  const selectedEstado = localidades.estados.find((item) => String(item.id) === anuncio.estadoId)
  const selectedCidade = localidades.cidades.find((item) => String(item.id) === anuncio.cidadeId)
  const selectedBairro = localidades.bairros.find((item) => String(item.id) === anuncio.bairroId)
  const estadoNome = selectedEstado?.nome || ''
  const estadoUf = selectedEstado?.uf || ''
  const cidadeNome = selectedCidade?.nome || ''
  const bairroNome = selectedBairro?.nome || ''
  const selectedStateLabel = estadoNome
    ? `${estadoNome}${estadoUf ? ` · ${estadoUf}` : ''}`
    : 'Selecione o estado'
  const previewLocation = [cidadeNome || 'Cidade', bairroNome || 'Bairro'].filter(Boolean).join(' · ')
  const previewReference = anuncio.pontoReferenciaTexto.trim()
  const previewDescription =
    anuncio.descricao.trim() ||
    profileDescription ||
    'Seu texto de apresentação aparece aqui para aproximar o preview do anúncio real.'
  const previewPrice = anuncio.preco.trim() || 'Consulte valores'
  const previewMedia = [...anuncio.fotosExistentes, ...fotoPreviewUrls]
  const hasVirtual =
    anuncio.servicos.includes('VIDEOCHAMADA') || anuncio.categoria === 'VENDA_DE_CONTEUDO'
  const quietPreview = currentStep === 'perfil' || currentStep === 'localizacao'
  const highlightedPreview = currentStep === 'fotos' || currentStep === 'revisao'
  const categoriaLabel =
    categorias.find((item) => item.value === anuncio.categoria)?.label || 'Categoria'
  const descricaoPerfilCount = descricaoPerfil.trim().length
  const descricaoPerfilNeedsMore = descricaoPerfilCount > 0 && descricaoPerfilCount < 20
  const descricaoPerfilRemaining = Math.max(0, 20 - descricaoPerfilCount)

  const stateOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.estados.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${item.uf ?? ''}`,
        subtitle: item.uf ? `UF ${item.uf}` : undefined,
      })),
    [localidades.estados]
  )

  const cidadeOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.cidades.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${estadoNome} ${estadoUf}`,
      })),
    [localidades.cidades, estadoNome, estadoUf]
  )

  const bairroOptions = useMemo<SearchableSelectOption[]>(
    () =>
      localidades.bairros.map((item) => ({
        id: String(item.id),
        label: item.nome,
        searchLabel: `${item.nome} ${cidadeNome} ${estadoNome} ${estadoUf}`,
      })),
    [localidades.bairros, cidadeNome, estadoNome, estadoUf]
  )

  const nextStep = () => {
    const next = editWizardSteps[Math.min(currentIndex + 1, editWizardSteps.length - 1)]
    setCurrentStep(next.id)
  }

  const previousStep = () => {
    const previous = editWizardSteps[Math.max(currentIndex - 1, 0)]
    setCurrentStep(previous.id)
  }

  const toggle = (field: 'locaisAtendimento' | 'servicos', value: string) => {
    if (field === 'locaisAtendimento') anuncio.toggleLocalAtendimento(value)
    else anuncio.toggleServico(value)
  }

  const getFirstInvalidStep = () => {
    if (anuncio.titulo.trim().length < 3 || !anuncio.categoria) {
      return { step: 'perfil' as const, message: 'Preencha nome e categoria para continuar.' }
    }
    if (!anuncio.estadoId || !anuncio.cidadeId || !anuncio.bairroId) {
      return {
        step: 'localizacao' as const,
        message: 'Escolha estado, cidade e bairro para continuar.',
      }
    }

    const preco = Number(anuncio.preco.replace(/\D/g, '')) / 100
    if (
      !Number.isFinite(preco) ||
      preco <= 0 ||
      !anuncio.horario ||
      anuncio.locaisAtendimento.length === 0 ||
      anuncio.servicos.length === 0
    ) {
      return {
        step: 'servicos' as const,
        message: 'Informe preço, horário, local de atendimento e ao menos um serviço.',
      }
    }

    if (photoCount === 0) {
      return {
        step: 'fotos' as const,
        message:
          anuncio.fotosExistentes.length === 0 && anuncio.fotosNovas.length === 0
            ? 'Envie ao menos uma foto para seguir.'
            : 'Selecione novamente as fotos antes de salvar.',
      }
    }

    if (anuncio.fotosErro) {
      return { step: 'fotos' as const, message: 'Resolva os erros das imagens antes de salvar.' }
    }

    return null
  }

  const syncProfileDescriptionIfNeeded = async () => {
    if (!usuario?.email) return
    const nextDescription = descricaoPerfil.trim()
    if (nextDescription === persistedProfileDescription) return
    await updateWizardProfileDescription({
      email: usuario.email,
      descricaoPerfil: nextDescription,
    })
  }

  const handleSubmit = async () => {
    const invalid = getFirstInvalidStep()
    if (invalid) {
      setCurrentStep(invalid.step)
      toast.warning(invalid.message)
      return
    }

    try {
      await syncProfileDescriptionIfNeeded()
      const saved = await anuncio.submit()
      await syncProgress('concluido', 'AGUARDANDO_MODERACAO', saved?.id || anuncio.anuncioId)
    } catch (err) {
      toast.error((err as any)?.message || 'Não foi possível salvar o anúncio.')
    }
  }

  const renderCurrentStep = () => {
    if (currentStep === 'perfil') {
      return (
        <WizardStepPerfil
          titulo={anuncio.titulo}
          categoria={anuncio.categoria}
          descricaoPerfil={descricaoPerfil}
          categorias={categorias}
          descricaoPerfilCount={descricaoPerfilCount}
          descricaoPerfilNeedsMore={descricaoPerfilNeedsMore}
          descricaoPerfilRemaining={descricaoPerfilRemaining}
          onTituloChange={anuncio.setTitulo}
          onCategoriaChange={anuncio.setCategoria}
          onDescricaoChange={setDescricaoPerfil}
        />
      )
    }

    if (currentStep === 'localizacao') {
      return (
        <WizardStepLocalizacao
          estadoId={anuncio.estadoId}
          cidadeId={anuncio.cidadeId}
          cidadeNome={cidadeNome}
          bairroId={anuncio.bairroId}
          bairroNome={bairroNome}
          pontoReferenciaTexto={anuncio.pontoReferenciaTexto}
          selectedStateLabel={selectedStateLabel}
          stateOptions={stateOptions}
          cidadeOptions={cidadeOptions}
          bairroOptions={bairroOptions}
          loadingEstados={localidades.loadingEstados}
          loadingCidades={localidades.loadingCidades}
          loadingBairros={localidades.loadingBairros}
          onEstado={anuncio.onSelectEstado}
          onCidade={anuncio.onSelectCidade}
          onBairro={anuncio.onSelectBairro}
          onReferencia={anuncio.setPontoReferenciaTexto}
        />
      )
    }

    if (currentStep === 'servicos') {
      return (
        <WizardStepServicos
          state={{
            titulo: anuncio.titulo,
            categoria: anuncio.categoria,
            descricaoPerfil,
            preco: anuncio.preco,
            horario: anuncio.horario,
            locaisAtendimento: anuncio.locaisAtendimento,
            servicos: anuncio.servicos,
            descricao: anuncio.descricao,
            linkConteudo: anuncio.linkConteudo,
            estadoId: anuncio.estadoId,
            cidadeId: anuncio.cidadeId,
            bairroId: anuncio.bairroId,
            estadoNome,
            estadoUf,
            cidadeNome,
            bairroNome,
            pontoReferenciaTexto: anuncio.pontoReferenciaTexto,
            fotos: anuncio.fotosNovas,
            fotoNomes: anuncio.fotosNovas.map((file) => file.name),
            premiumChoice: 'gratis',
          }}
          onToggle={toggle}
          onPatch={(payload) => {
            if (payload.preco !== undefined) anuncio.setPreco(payload.preco)
            if (payload.horario !== undefined) anuncio.setHorario(payload.horario)
            if (payload.descricao !== undefined) anuncio.setDescricao(payload.descricao)
            if (payload.linkConteudo !== undefined) anuncio.setLinkConteudo(payload.linkConteudo)
          }}
        />
      )
    }

    if (currentStep === 'fotos') {
      return (
        <WizardStepFotos
          initialFiles={anuncio.fotosNovas}
          initialUrls={anuncio.fotosExistentes}
          fotoNomes={anuncio.fotosNovas.map((file) => file.name)}
          onChange={anuncio.handleFotosChange}
          onChangeExistentes={anuncio.setFotosExistentes}
          onMediaTouched={anuncio.markFotosAlteradas}
          maxFotos={anuncio.maxFotos}
          maxMB={20}
          videosExistentes={anuncio.videosExistentes}
          onChangeVideosExistentes={anuncio.setVideosExistentes}
          videosNovos={anuncio.videosNovos}
          onChangeVideosNovos={anuncio.setVideosNovos}
          onVideoTouched={anuncio.markVideosAlterados}
          canUploadVideos={anuncio.canUploadVideos}
        />
      )
    }

    return (
      <WizardFinalReview
        previewTitle={previewTitle}
        categoriaLabel={categoriaLabel}
        previewLocation={previewLocation}
        previewReference={previewReference}
        state={{
          titulo: anuncio.titulo,
          categoria: anuncio.categoria,
          descricaoPerfil,
          preco: anuncio.preco,
          horario: anuncio.horario,
          locaisAtendimento: anuncio.locaisAtendimento,
          servicos: anuncio.servicos,
          descricao: anuncio.descricao,
          linkConteudo: anuncio.linkConteudo,
          estadoId: anuncio.estadoId,
          cidadeId: anuncio.cidadeId,
          bairroId: anuncio.bairroId,
          estadoNome,
          estadoUf,
          cidadeNome,
          bairroNome,
          pontoReferenciaTexto: anuncio.pontoReferenciaTexto,
          fotos: anuncio.fotosNovas,
          fotoNomes: anuncio.fotosNovas.map((file) => file.name),
          premiumChoice: 'gratis',
        }}
        hasVirtual={hasVirtual}
        photoCount={photoCount}
      />
    )
  }

  if (carregandoUsuario || anuncio.carregando) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-20 text-sm text-zinc-500">
        Preparando a edição do anúncio...
      </div>
    )
  }

  if (!usuario) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">Acesso restrito</h1>
        <p className="mt-2 text-zinc-600">Faça login para editar seu anúncio.</p>
      </div>
    )
  }

  if (anuncio.loadError) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-20 text-center">
        <h1 className="text-2xl font-semibold text-zinc-950">
          Não foi possível carregar o anúncio
        </h1>
        <p className="mt-2 text-zinc-600">{anuncio.loadError}</p>
        <Button
          type="button"
          onClick={anuncio.retryLoad}
          className="mt-6 rounded-full bg-zinc-950 px-5 text-white hover:bg-zinc-800"
        >
          Tentar novamente
        </Button>
      </div>
    )
  }

  return (
    <main className="min-h-screen bg-[#f7f4ef] text-zinc-950">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-5 sm:px-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:px-8 lg:py-8">
        <section
          ref={wizardTopRef}
          className="min-w-0 rounded-[30px] border border-zinc-200/90 bg-white shadow-[0_18px_60px_rgba(24,24,27,0.08)]"
        >
          <div className="border-b border-zinc-100 px-5 py-5 sm:px-7">
            <div className="flex items-center justify-between gap-3">
              <div>
                <h1 className="text-2xl font-semibold tracking-normal text-zinc-950 sm:text-3xl">
                  Editar anúncio
                </h1>
              </div>
              <div className="rounded-full border border-zinc-200 px-3 py-1.5 text-xs text-zinc-600">
                Mesmo fluxo, edição mais rápida
              </div>
            </div>

            <div
              className="mt-6 grid gap-1.5"
              style={{ gridTemplateColumns: `repeat(${editWizardSteps.length}, minmax(0, 1fr))` }}
            >
              {editWizardSteps.map((step, idx) => (
                <button
                  key={step.id}
                  type="button"
                  onClick={() => {
                    setCurrentStep(step.id)
                  }}
                  className={cn(
                    'h-2 rounded-full transition',
                    idx <= currentIndex
                      ? 'bg-gradient-to-r from-rose-500 via-fuchsia-500 to-violet-500'
                      : 'bg-zinc-200'
                  )}
                  aria-label={`Ir para ${step.title}`}
                />
              ))}
            </div>
          </div>

          <div className="px-5 py-6 sm:px-7 sm:py-7">
            <WizardPreview
              showDesktop={false}
              open={previewOpen}
              onOpenChange={setPreviewOpen}
              onOpenRequest={() => {
                setPreviewHintDismissed(true)
                setPreviewOpen(true)
              }}
              quietPreview={quietPreview}
              highlightedPreview={highlightedPreview}
              previewHintActive={!previewHintDismissed}
              previewTitle={previewTitle}
              previewPrice={previewPrice}
              previewDescription={previewDescription}
              previewMedia={previewMedia}
              previewReference={previewReference}
              idade={idade}
              hasVirtual={hasVirtual}
              hasExistingKyc={hasExistingKyc}
              premiumChoice="gratis"
              estadoUf={estadoUf}
              cidadeNome={cidadeNome}
              bairroNome={bairroNome}
            />

            <div className="mt-7">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-rose-600">
                {editWizardSteps[currentIndex].eyebrow}
              </p>
              <h2 className="mt-2 text-2xl font-semibold tracking-normal text-zinc-950">
                {stepCopy[currentStep]?.title || editWizardSteps[currentIndex].title}
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-600">
                {currentStep === 'revisao'
                  ? 'Confira as informações principais antes de salvar. Se algo precisar de ajuste, você pode voltar sem perder o contexto.'
                  : stepCopy[currentStep]?.description}
              </p>
            </div>

            <div className="mt-8">{renderCurrentStep()}</div>

            {anuncio.fotosErro ? (
              <p className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                {anuncio.fotosErro}
              </p>
            ) : null}

            <div className="mt-10 flex items-center justify-between gap-3 border-t border-zinc-100 pt-6">
              <Button
                type="button"
                variant="outline"
                onClick={previousStep}
                disabled={currentIndex === 0 || anuncio.salvando}
                className="h-12 rounded-full border-zinc-200 px-5"
              >
                <ChevronLeft className="mr-2 h-4 w-4" />
                Voltar
              </Button>

              {currentStep === 'revisao' ? (
                <Button
                  type="button"
                  onClick={() => void handleSubmit()}
                  disabled={anuncio.salvando}
                  className="h-12 rounded-full bg-zinc-950 px-6 text-white hover:bg-zinc-800"
                >
                  {anuncio.salvando ? 'Salvando…' : 'Salvar alterações'}
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={nextStep}
                  disabled={anuncio.salvando}
                  className="h-12 rounded-full bg-[#FC1EAD] px-6 text-white hover:bg-[#e01a9a]"
                >
                  Continuar
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </section>

        <WizardPreview
          showMobile={false}
          renderDialog
          open={previewOpen}
          onOpenChange={setPreviewOpen}
          onOpenRequest={() => {
            setPreviewHintDismissed(true)
            setPreviewOpen(true)
          }}
          quietPreview={quietPreview}
          highlightedPreview={highlightedPreview}
          previewHintActive={!previewHintDismissed}
          previewTitle={previewTitle}
          previewPrice={previewPrice}
          previewDescription={previewDescription}
          previewMedia={previewMedia}
          previewReference={previewReference}
          idade={idade}
          hasVirtual={hasVirtual}
          hasExistingKyc={hasExistingKyc}
          premiumChoice="gratis"
          estadoUf={estadoUf}
          cidadeNome={cidadeNome}
          bairroNome={bairroNome}
        />
      </div>
    </main>
  )
}
