'use client'

import { ChevronRight, Crown, ImageIcon, ShieldCheck, Sparkles, X } from 'lucide-react'
import { AnuncioCard } from '@/components/anuncios/anuncio-card'
import { ImagemProprietario } from '@/components/anuncios/imagem-proprietario'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import { PreviewTag } from './wizard-ui'

export function WizardPreview({
  showMobile = true,
  showDesktop = true,
  renderDialog = false,
  open,
  onOpenChange,
  onOpenRequest,
  highlightedPreview,
  quietPreview,
  previewHintActive,
  previewTitle,
  previewPrice,
  previewDescription,
  previewMedia,
  previewExpiraEm,
  onPreviewRefresh,
  previewReference,
  idade,
  hasVirtual,
  hasExistingKyc,
  premiumChoice,
  estadoUf,
  cidadeNome,
  bairroNome,
}: {
  showMobile?: boolean
  showDesktop?: boolean
  renderDialog?: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  onOpenRequest: () => void
  highlightedPreview: boolean
  quietPreview: boolean
  previewHintActive: boolean
  previewTitle: string
  previewPrice: string
  previewDescription: string
  previewMedia: string[]
  previewExpiraEm?: string | null
  onPreviewRefresh?: () => Promise<void>
  previewReference: string
  idade: number | null
  hasVirtual: boolean
  hasExistingKyc: boolean
  premiumChoice: 'gratis' | 'destaque'
  estadoUf: string
  cidadeNome: string
  bairroNome: string
}) {
  const thumb = previewMedia[0]

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
          {thumb ? (
            <ImagemProprietario src={thumb} alt="" expiresAt={previewExpiraEm} onRefresh={onPreviewRefresh} interactive={false} />
          ) : (
            <ImageIcon className="h-5 w-5 text-zinc-400" />
          )}
        </div>

        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-zinc-950">{previewTitle}</p>
          <p className="mt-1 text-xs font-medium text-zinc-600">Pré-visualizar anúncio</p>
          <p className="mt-1 truncate text-[11px] text-zinc-500">
            {highlightedPreview
              ? 'Abra para revisar o resultado com mais atenção.'
              : 'Veja como seu card está ficando sem sair do fluxo.'}
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
      {showMobile && <div className="mt-5 lg:hidden">{launcher}</div>}

      {showDesktop && (
        <aside className="hidden lg:sticky lg:top-6 lg:block lg:self-start">
          <div className={cn('space-y-4', quietPreview && 'opacity-95')}>
            {launcher}

            <div className="rounded-[24px] border border-zinc-200 bg-white/88 px-5 py-4 shadow-sm backdrop-blur-sm">
              <div className="flex flex-wrap gap-2">
                {premiumChoice === 'destaque' && (
                  <PreviewTag
                    icon={<Crown className="h-3.5 w-3.5" />}
                    label="Destaque"
                    tone="light"
                  />
                )}
                {hasVirtual && (
                  <PreviewTag
                    icon={<Sparkles className="h-3.5 w-3.5" />}
                    label="Atendimento Virtual"
                    tone="light"
                  />
                )}
                <PreviewTag
                  icon={<ShieldCheck className="h-3.5 w-3.5" />}
                  label={hasExistingKyc ? 'Conta verificada' : 'Conta não verificada'}
                  tone="light"
                />
              </div>
            </div>
          </div>
        </aside>
      )}

      {renderDialog && (
        <Dialog open={open} onOpenChange={onOpenChange}>
          <DialogContent
            showCloseButton={false}
            className="bottom-0 left-1/2 top-auto w-[100vw] max-w-none translate-x-[-50%] translate-y-0 rounded-t-[30px] rounded-b-none border border-zinc-200 bg-[#f7f4ef] p-0 shadow-[0_-12px_42px_rgba(24,24,27,0.18)] sm:bottom-auto sm:top-1/2 sm:w-[min(92vw,1040px)] sm:max-w-[1040px] sm:translate-y-[-50%] sm:rounded-[32px] sm:shadow-[0_24px_80px_rgba(24,24,27,0.22)]"
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
                    Visualize como seu anúncio está ficando
                  </DialogTitle>
                  <DialogDescription className="text-sm leading-6 text-zinc-600">
                    Confira o card com a mesma estética do site antes de seguir.
                  </DialogDescription>
                </DialogHeader>
              </div>

              <div className="grid gap-5 px-4 py-4 sm:px-6 sm:py-6 lg:grid-cols-[360px_minmax(0,1fr)]">
                <div className="mx-auto w-full max-w-[330px]">
                  <AnuncioCard
                    id={0}
                    slug="preview-anuncio"
                    nome={previewTitle}
                    estadoUf={estadoUf || null}
                    cidadeNome={cidadeNome || null}
                    bairroNome={bairroNome || null}
                    pontoReferenciaTexto={previewReference || null}
                    idade={idade}
                    valor={previewPrice}
                    previewImagens={previewMedia}
                    previewExpiraEm={previewExpiraEm}
                    onPreviewRefresh={onPreviewRefresh}
                    descricao={previewDescription}
                    destaque={premiumChoice === 'destaque'}
                    carrosselDisponivel={false}
                    videoHabilitado={false}
                    whatsappCardEnabled={false}
                    previewMode
                  />
                </div>

                <div className="space-y-4">
                  <div className="rounded-[26px] border border-zinc-200 bg-white px-5 py-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-zinc-950">O que já transmite bem</h3>
                    <ul className="mt-3 space-y-2 text-sm leading-6 text-zinc-600">
                      <li>Nome e categoria já ajudam o visitante a entender seu perfil.</li>
                      <li>A localização contextual aproxima o anúncio da busca local.</li>
                      <li>Uma descrição bem escrita passa mais confiança logo no primeiro contato.</li>
                    </ul>
                  </div>

                  <div className="rounded-[26px] border border-zinc-200 bg-white px-5 py-4 shadow-sm">
                    <h3 className="text-sm font-semibold text-zinc-950">
                      Detalhes que valorizam seu anúncio
                    </h3>
                    <ul className="mt-3 space-y-2 text-sm leading-6 text-zinc-600">
                      <li>Fotos bem iluminadas costumam receber mais visualizações.</li>
                      <li>Perfis premium podem adicionar mais fotos, vídeos e destaque nas listagens.</li>
                      <li>Na revisão final você consegue conferir tudo antes do envio para moderação.</li>
                    </ul>
                  </div>

                  <div className="rounded-[26px] border border-zinc-200 bg-zinc-950 px-5 py-4 text-white shadow-sm">
                    <p className="text-sm font-semibold">
                      {hasExistingKyc
                        ? 'Sua conta já está pronta para publicar quando você quiser.'
                        : 'Quando chegar ao final, concluiremos rapidamente a verificação da conta.'}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      )}
    </>
  )
}
