'use client'

import { ChevronRight, Crown, Film, ImageIcon, Layers3, MessageCircleMore, Sparkles, X } from 'lucide-react'
import { AnuncioCard } from '@/components/anuncios/anuncio-card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { PreviewTag } from '@/features/anuncio-wizard/components/wizard-ui'

type PreviewSnapshot = {
  title: string
  price: string
  location: {
    estadoUf?: string | null
    cidadeNome?: string | null
    bairroNome?: string | null
    pontoReferenciaTexto?: string | null
  }
  description: string
  images: string[]
  videos: string[]
  idade: number | null
  destaque: boolean
  carrosselDisponivel: boolean
  videoHabilitado: boolean
  whatsappCardEnabled: boolean
}

function MonetizacaoTagBar({ snapshot }: { snapshot: PreviewSnapshot }) {
  return (
    <div className="flex flex-wrap gap-2">
      {snapshot.destaque ? (
        <PreviewTag icon={<Crown className="h-3.5 w-3.5" />} label="Topo da lista" tone="light" />
      ) : null}
      {snapshot.videoHabilitado ? (
        <PreviewTag icon={<Film className="h-3.5 w-3.5" />} label="Vídeo" tone="light" />
      ) : null}
      {snapshot.carrosselDisponivel ? (
        <PreviewTag icon={<Layers3 className="h-3.5 w-3.5" />} label="Carrossel" tone="light" />
      ) : null}
      {snapshot.whatsappCardEnabled ? (
        <PreviewTag
          icon={<MessageCircleMore className="h-3.5 w-3.5" />}
          label="WhatsApp destacado"
          tone="light"
        />
      ) : null}
    </div>
  )
}

function PreviewCardColumn({
  label,
  snapshot,
}: {
  label: string
  snapshot: PreviewSnapshot
}) {
  const firstImage = snapshot.images[0]

  return (
    <div className="space-y-4">
      <div className="rounded-[26px] border border-zinc-200 bg-white px-5 py-4 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-500">{label}</p>
            <h3 className="mt-2 text-lg font-semibold text-zinc-950">{snapshot.title}</h3>
          </div>
          <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-zinc-200 bg-zinc-50">
            {firstImage ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={firstImage} alt="" className="h-full w-full object-cover" />
            ) : (
              <ImageIcon className="h-5 w-5 text-zinc-400" />
            )}
          </div>
        </div>

        <div className="mt-4">
          <MonetizacaoTagBar snapshot={snapshot} />
        </div>
      </div>

      <div className="mx-auto w-full max-w-[330px]">
        <AnuncioCard
          id={0}
          slug="preview-monetizacao"
          nome={snapshot.title}
          estadoUf={snapshot.location.estadoUf ?? null}
          cidadeNome={snapshot.location.cidadeNome ?? null}
          bairroNome={snapshot.location.bairroNome ?? null}
          pontoReferenciaTexto={snapshot.location.pontoReferenciaTexto ?? null}
          idade={snapshot.idade}
          valor={snapshot.price}
          imagens={snapshot.images}
          videos={snapshot.videos}
          descricao={snapshot.description}
          destaque={snapshot.destaque}
          carrosselDisponivel={snapshot.carrosselDisponivel}
          videoHabilitado={snapshot.videoHabilitado}
          whatsappCardEnabled={snapshot.whatsappCardEnabled}
          previewMode
        />
      </div>
    </div>
  )
}

export function MonetizacaoWizardPreview({
  showMobile = true,
  showDesktop = true,
  renderDialog = true,
  open,
  onOpenChange,
  onOpenRequest,
  highlightedPreview,
  previewHintActive,
  title,
  before,
  after,
}: {
  showMobile?: boolean
  showDesktop?: boolean
  renderDialog?: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenRequest: () => void
  highlightedPreview: boolean
  previewHintActive: boolean
  title: string
  before: PreviewSnapshot
  after: PreviewSnapshot
}) {
  const launcherImage = after.images[0] || before.images[0]

  const launcher = (
    <button
      type="button"
      onClick={onOpenRequest}
      className={cn(
        'group relative flex h-[88px] w-full items-center justify-between overflow-hidden rounded-[24px] border px-4 py-3 text-left shadow-sm transition-all duration-300',
        highlightedPreview
          ? 'border-zinc-950/10 bg-white shadow-[0_16px_36px_rgba(24,24,27,0.08)]'
          : 'border-zinc-200 bg-zinc-50/90',
        'hover:border-zinc-300 hover:bg-white'
      )}
    >
      <div className="flex min-w-0 items-center gap-3 pr-3">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-zinc-200 bg-white">
          {launcherImage ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={launcherImage} alt="" className="h-full w-full object-cover" />
          ) : (
            <ImageIcon className="h-5 w-5 text-zinc-400" />
          )}
        </div>

        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-zinc-950">{title}</p>
          <p className="mt-1 text-xs font-medium text-zinc-600">Pré-visualizar anúncio</p>
          <p className="mt-1 truncate text-[11px] text-zinc-500">
            Compare o card atual com o resultado depois da ativação.
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div
          className={cn(
            'flex h-11 w-11 shrink-0 items-center justify-center rounded-full border bg-white text-zinc-700 transition-transform duration-300',
            highlightedPreview ? 'border-zinc-300' : 'border-zinc-200',
            previewHintActive && 'motion-safe:animate-pulse'
          )}
        >
          <ChevronRight className="h-4 w-4" />
        </div>
      </div>

      <div
        className={cn(
          'pointer-events-none absolute inset-x-0 bottom-0 h-[2px] transition-opacity duration-300',
          highlightedPreview
            ? 'bg-gradient-to-r from-rose-500 via-fuchsia-500 to-violet-500 opacity-100'
            : 'bg-gradient-to-r from-zinc-200 via-zinc-300 to-zinc-200 opacity-70'
        )}
      />
    </button>
  )

  return (
    <>
      {showMobile ? <div className="mt-5 lg:hidden">{launcher}</div> : null}

      {showDesktop ? (
        <aside className="hidden lg:sticky lg:top-6 lg:block lg:self-start">
          <div className="space-y-4">
            {launcher}

            <div className="rounded-[24px] border border-zinc-200 bg-white/88 px-5 py-4 shadow-sm backdrop-blur-sm">
              <div className="flex flex-wrap gap-2">
                <PreviewTag icon={<Crown className="h-3.5 w-3.5" />} label="Antes e depois" tone="light" />
                <PreviewTag icon={<Sparkles className="h-3.5 w-3.5" />} label="Mesmo card do site" tone="light" />
              </div>
              <p className="mt-3 text-sm leading-6 text-zinc-600">
                Abra a prévia para enxergar o impacto visual antes de usar seus créditos.
              </p>
            </div>
          </div>
        </aside>
      ) : null}

      {renderDialog ? (
        <Dialog open={open} onOpenChange={onOpenChange}>
          <DialogContent
            showCloseButton={false}
            className="bottom-0 left-1/2 top-auto w-[100vw] max-w-none translate-x-[-50%] translate-y-0 rounded-t-[30px] rounded-b-none border border-zinc-200 bg-[#f7f4ef] p-0 shadow-[0_-12px_42px_rgba(24,24,27,0.18)] sm:bottom-auto sm:top-1/2 sm:w-[min(94vw,1180px)] sm:max-w-[1180px] sm:translate-y-[-50%] sm:rounded-[32px] sm:shadow-[0_24px_80px_rgba(24,24,27,0.22)]"
          >
            <div className="max-h-[86svh] overflow-y-auto pb-[calc(env(safe-area-inset-bottom)+1rem)] sm:max-h-[86vh] sm:pb-6">
              <div className="sticky top-0 z-10 border-b border-zinc-200 bg-white/95 px-5 py-4 backdrop-blur-sm sm:px-6">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <div className="text-[11px] font-semibold uppercase tracking-[0.16em] text-zinc-500">
                    Pré-visualização
                  </div>
                  <DialogClose asChild>
                    <button
                      type="button"
                      className="flex h-10 w-10 items-center justify-center rounded-full border border-zinc-200 bg-white text-zinc-700 shadow-sm transition hover:border-zinc-300 hover:bg-zinc-50"
                      aria-label="Fechar pré-visualização"
                    >
                      <X className="h-4 w-4" />
                    </button>
                  </DialogClose>
                </div>
                <DialogHeader className="space-y-2 text-left">
                  <DialogTitle className="text-xl font-semibold tracking-normal text-zinc-950">
                    Veja o impacto visual da monetização
                  </DialogTitle>
                  <DialogDescription className="text-sm leading-6 text-zinc-600">
                    O comparativo usa o mesmo card do site em modo de pré-visualização.
                  </DialogDescription>
                </DialogHeader>
              </div>

              <div className="grid gap-5 px-4 py-4 sm:px-6 sm:py-6 lg:grid-cols-2">
                <PreviewCardColumn label="Antes" snapshot={before} />
                <PreviewCardColumn label="Depois" snapshot={after} />
              </div>
            </div>
          </DialogContent>
        </Dialog>
      ) : null}
    </>
  )
}
