"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import {
  XMarkIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  ExclamationTriangleIcon,
  MapPinIcon,
} from "@heroicons/react/24/solid"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { VisitorVerificationModal } from "@/components/compliance/visitor-verification-modal"
import type { StoryBundle, StoryItem, StoryViewerItem } from "./stories-types"
import {
  getInitials,
  loginPublicoDoBundle,
  rotuloPublicoComIdade,
  rotuloPublicoDoBundle,
} from "./stories-types"
import { canNavigateFromStory, sanitizeStoryViewerItem } from "./story-access-policy"
import { publicApiUrl } from '@/lib/api-contract'

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  bundles: StoryBundle[]
  initialBundleIndex: number
  onVerificationRefresh?: () => Promise<void> | void
  onStoryCurrent?: (item: StoryItem) => void
}

const IMAGE_MS = 5500

function StoryStateCard({
  title,
  description,
  primaryAction,
}: {
  title: string
  description: string
  primaryAction?: { label: string; onClick: () => void }
}) {
  return (
    <div className="relative flex h-[100svh] w-full max-w-[100vw] items-center justify-center overflow-hidden bg-gradient-to-br from-gray-950 via-black to-gray-900 px-6">
      <div
        className="relative z-20 w-full max-w-sm rounded-3xl border border-white/10 bg-white/5 p-6 text-white shadow-2xl backdrop-blur-sm"
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
        }}
      >
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-white/10">
          <ExclamationTriangleIcon className="h-6 w-6 text-white" />
        </div>
        <p className="text-base font-semibold leading-snug sm:text-lg">{title}</p>
        <p className="mt-2 text-sm leading-relaxed text-white/80">{description}</p>
        {primaryAction ? (
          <Button
            type="button"
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              primaryAction.onClick()
            }}
            className="mt-5 h-10 w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          >
            {primaryAction.label}
          </Button>
        ) : null}
      </div>
    </div>
  )
}

export function StoryViewerDialog({
  open,
  onOpenChange,
  bundles,
  initialBundleIndex,
  onVerificationRefresh,
  onStoryCurrent,
}: Props) {
  const router = useRouter()
  const [bundleIndex, setBundleIndex] = useState(0)
  const [itemIndex, setItemIndex] = useState(0)
  const [videoProg, setVideoProg] = useState(0)
  const [verificationOpen, setVerificationOpen] = useState(false)
  const [viewerItem, setViewerItem] = useState<StoryViewerItem | null>(null)
  const [viewerLoading, setViewerLoading] = useState(false)
  const [viewerError, setViewerError] = useState<string | null>(null)
  const [mediaError, setMediaError] = useState(false)
  const [mediaReady, setMediaReady] = useState(false)
  const [reloadTick, setReloadTick] = useState(0)
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const anuncioStoryRef = useRef<HTMLElement | null>(null)
  const viewedStoryRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    setBundleIndex(Math.max(0, Math.min(initialBundleIndex, bundles.length - 1)))
    setItemIndex(0)
    setVideoProg(0)
    setViewerItem(null)
    setViewerError(null)
    setMediaError(false)
    setMediaReady(false)
    viewedStoryRef.current = null
  }, [open, initialBundleIndex, bundles.length])

  const currentBundle = bundles[bundleIndex]
  const currentFeedItem = currentBundle?.itens?.[itemIndex]

  useEffect(() => {
    if (!open || !currentFeedItem?.storyId) {
      setViewerItem(null)
      setViewerError(null)
      setViewerLoading(false)
      return
    }

    let cancelled = false
    setViewerLoading(true)
    setViewerError(null)
    setViewerItem(null)
    setMediaError(false)

    void fetch(publicApiUrl(`/stories/${currentFeedItem.storyId}`), {
      credentials: "include",
      cache: "no-store",
    })
      .then(async (res) => {
        const data = await res.json().catch(() => null)
        if (!res.ok) {
          throw new Error(data?.message || data?.error || "Nao foi possivel carregar o story.")
        }
        if (!cancelled) {
          setViewerItem(sanitizeStoryViewerItem(data) as StoryViewerItem)
        }
      })
      .catch((error: any) => {
        if (!cancelled) {
          setViewerError(error?.message || "Nao foi possivel carregar o story.")
        }
      })
      .finally(() => {
        if (!cancelled) {
          setViewerLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [currentFeedItem?.storyId, open, reloadTick])

  useEffect(() => {
    setVideoProg(0)
    setMediaError(false)
    setMediaReady(false)
    viewedStoryRef.current = null
  }, [bundleIndex, itemIndex, reloadTick])

  const markCurrentStoryVisible = useCallback(() => {
    if (!currentFeedItem || viewerItem?.viewerState !== "LIBERADO") return
    if (viewerItem.modoConteudo !== "ANUNCIO" && !viewerItem.midiaUrl) return
    setMediaReady(true)
    const identity = String(currentFeedItem.storyId)
    if (viewedStoryRef.current === identity) return
    viewedStoryRef.current = identity
    onStoryCurrent?.(currentFeedItem)
  }, [currentFeedItem, onStoryCurrent, viewerItem])

  useEffect(() => {
    if (!open || viewerItem?.viewerState !== "LIBERADO" || viewerItem.modoConteudo !== "ANUNCIO") return
    const node = anuncioStoryRef.current
    if (!node || typeof IntersectionObserver === "undefined") return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting && entry.intersectionRatio >= 0.6)) {
        markCurrentStoryVisible()
      }
    }, { threshold: [0.6] })
    observer.observe(node)
    return () => observer.disconnect()
  }, [markCurrentStoryVisible, open, viewerItem?.modoConteudo, viewerItem?.storyId, viewerItem?.viewerState])

  const nextStory = useCallback(() => {
    const bundle = bundles[bundleIndex]
    if (!bundle) return

    const nextItem = itemIndex + 1
    if (nextItem < bundle.itens.length) {
      setItemIndex(nextItem)
      return
    }

    const nextBundle = bundleIndex + 1
    if (nextBundle < bundles.length) {
      setBundleIndex(nextBundle)
      setItemIndex(0)
      return
    }

    onOpenChange(false)
  }, [bundleIndex, bundles, itemIndex, onOpenChange])

  const prevStory = useCallback(() => {
    const bundle = bundles[bundleIndex]
    if (!bundle) return

    const prevItem = itemIndex - 1
    if (prevItem >= 0) {
      setItemIndex(prevItem)
      return
    }

    const prevBundle = bundleIndex - 1
    if (prevBundle >= 0) {
      const previous = bundles[prevBundle]
      setBundleIndex(prevBundle)
      setItemIndex(Math.max(0, (previous?.itens?.length || 1) - 1))
    }
  }, [bundleIndex, bundles, itemIndex])

  useEffect(() => {
    if (!open) return
    if (verificationOpen || !mediaReady) return

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "ArrowRight") nextStory()
      if (e.key === "ArrowLeft") prevStory()
    }

    window.addEventListener("keydown", onKeyDown)
    return () => window.removeEventListener("keydown", onKeyDown)
  }, [mediaReady, nextStory, open, prevStory, verificationOpen])

  useEffect(() => {
    if (!open) return
    if (verificationOpen) return
    if (!mediaReady) return
    if (!viewerItem) return
    if (viewerLoading) return
    if (viewerError) return
    if (viewerItem.viewerState !== "LIBERADO") return
    if (viewerItem.tipo === "VIDEO") return
    if (viewerItem.modoConteudo !== "ANUNCIO" && !viewerItem.midiaUrl) return

    const t = setTimeout(() => nextStory(), IMAGE_MS)
    return () => clearTimeout(t)
  }, [mediaReady, nextStory, open, verificationOpen, viewerError, viewerItem, viewerLoading])

  const progressBars = useMemo(() => currentBundle?.itens || [], [currentBundle])

  const loginBundle = currentBundle ? loginPublicoDoBundle(currentBundle) : ""
  const rotuloBundle = currentBundle ? rotuloPublicoDoBundle(currentBundle) : "Perfil"
  const loginViewer =
    viewerItem?.profileNavigable && viewerItem?.usuarioUsername
      ? viewerItem.usuarioUsername.trim()
      : loginBundle
  const anuncioSlugViewer = (viewerItem?.anuncioSlug ?? currentFeedItem?.anuncioSlug ?? "").trim()
  const perfilNavegavel = Boolean(
    (viewerItem?.profileNavigable && loginViewer) || (currentBundle?.profileNavigable && loginBundle)
  )
  const anuncioNavegavel = Boolean(anuncioSlugViewer)
  const podeNavegarAnuncio = anuncioNavegavel
    && canNavigateFromStory(viewerItem, mediaReady, verificationOpen)
  const rotuloPerfil =
    viewerItem?.profileNavigable && viewerItem?.displayUsername
      ? `@${viewerItem.displayUsername}`
      : rotuloBundle
  const rotuloPerfilComIdade = rotuloPublicoComIdade(
    rotuloPerfil,
    viewerItem?.idade ?? currentFeedItem?.idade ?? currentBundle?.idade,
  )

  function irParaAnuncioDoStory() {
    if (!anuncioSlugViewer || !podeNavegarAnuncio) {
      console.warn("[stories] navegacao indisponivel enquanto a midia nao estiver liberada.")
      return
    }
    onOpenChange(false)
    router.push(`/anuncios/${encodeURIComponent(anuncioSlugViewer)}`)
  }

  async function refreshViewerAndFeed() {
    if (onVerificationRefresh) {
      await onVerificationRefresh()
    }
    setReloadTick((prev) => prev + 1)
  }

  const isFirst = bundleIndex === 0 && itemIndex === 0
  const isLast =
    bundles.length > 0 &&
    bundleIndex === bundles.length - 1 &&
    itemIndex === (currentBundle?.itens?.length || 1) - 1
  const viewerInteracoesTravadas = verificationOpen
  const viewerNavegacaoTravada = !canNavigateFromStory(viewerItem, mediaReady, verificationOpen)

  function renderViewerBody() {
    if (!currentFeedItem) {
      return (
        <div className="flex h-[100svh] w-full items-center justify-center text-white/70">
          Sem story
        </div>
      )
    }

    if (viewerLoading) {
      return (
        <div className="flex h-[100svh] w-full items-center justify-center">
          <div className="rounded-3xl border border-white/10 bg-white/5 px-6 py-5 text-sm text-white/80">
            Carregando story...
          </div>
        </div>
      )
    }

    if (viewerError) {
      return (
        <StoryStateCard
          title="Nao foi possivel carregar este story"
          description={viewerError}
          primaryAction={{ label: "Tentar novamente", onClick: () => setReloadTick((prev) => prev + 1) }}
        />
      )
    }

    if (!viewerItem) {
      return (
        <StoryStateCard
          title="Story indisponivel"
          description="Os dados deste story nao puderam ser carregados."
          primaryAction={{ label: "Tentar novamente", onClick: () => setReloadTick((prev) => prev + 1) }}
        />
      )
    }

    if (viewerItem.viewerState === "IDADE_NAO_CONFIRMADA") {
      return (
        <StoryStateCard
          title="Conteudo restrito apenas para maiores de 18 anos"
          description="A visualizacao deste story exige verificacao valida."
          primaryAction={{
            label: "Confirmar maioridade",
            onClick: () => setVerificationOpen(true),
          }}
        />
      )
    }

    if (viewerItem.viewerState === "INDISPONIVEL") {
      return (
        <StoryStateCard
          title="Midia indisponivel no momento"
          description="Nao foi possivel exibir este story agora."
          primaryAction={{ label: "Tentar novamente", onClick: () => setReloadTick((prev) => prev + 1) }}
        />
      )
    }

    if (viewerItem.viewerState === "ERRO_DADOS") {
      return (
        <StoryStateCard
          title="Story com dados inconsistentes"
          description="Este story nao esta apto para exibicao publica."
        />
      )
    }

    if (viewerItem.modoConteudo === "ANUNCIO") {
      const local = [viewerItem.cidade, viewerItem.uf].filter(Boolean).join(" - ")
      const preco = viewerItem.preco == null
        ? null
        : Number(viewerItem.preco).toLocaleString("pt-BR", { style: "currency", currency: "BRL" })
      return (
        <article
          ref={anuncioStoryRef}
          className="relative z-20 flex min-h-[100svh] w-full max-w-xl flex-col justify-center bg-slate-950 px-8 py-28 text-white sm:px-12"
          onClick={(event) => event.stopPropagation()}
          aria-label="Apresentação do anúncio no Story"
        >
          <div className="h-1 w-16 rounded-full bg-[#FC1EAD]" aria-hidden="true" />
          <p className="mt-6 text-xs font-semibold uppercase text-pink-300">Anúncio em destaque</p>
          <h2 className="mt-3 break-words text-3xl font-bold leading-tight sm:text-4xl">
            {viewerItem.displayUsername || "Anúncio"}
          </h2>
          {viewerItem.idade != null ? <p className="mt-2 text-lg text-white/85">{viewerItem.idade} anos</p> : null}
          {local ? (
            <p className="mt-5 flex items-center gap-2 text-sm text-white/80">
              <MapPinIcon className="h-5 w-5 shrink-0 text-[#FC1EAD]" aria-hidden="true" />
              {local}
            </p>
          ) : null}
          {preco ? <p className="mt-4 text-2xl font-bold text-pink-300">{preco}</p> : null}
          {viewerItem.resumo ? <p className="mt-5 max-w-prose text-sm leading-6 text-white/75">{viewerItem.resumo}</p> : null}
          <Button
            type="button"
            onClick={irParaAnuncioDoStory}
            disabled={!podeNavegarAnuncio}
            className="mt-8 min-h-12 w-full bg-[#FC1EAD] text-white hover:bg-[#e01a9a]"
          >
            Ver anúncio
          </Button>
        </article>
      )
    }

    if (mediaError || !viewerItem.midiaUrl) {
      return (
        <StoryStateCard
          title="Nao foi possivel carregar a midia"
          description="A midia permanece bloqueada. Tente carregar novamente."
          primaryAction={{ label: "Tentar novamente", onClick: () => setReloadTick((prev) => prev + 1) }}
        />
      )
    }

    if (viewerItem.tipo === "VIDEO") {
      return (
        <video
          key={`${viewerItem.storyId}:${viewerItem.midiaUrl}`}
          ref={videoRef}
          src={viewerItem.midiaUrl}
          className="h-[100svh] w-full max-w-[100vw] bg-black object-contain"
          autoPlay
          muted
          playsInline
          preload="metadata"
          controls={false}
          onLoadedData={(e) => {
            void e.currentTarget.play().catch(() => {})
          }}
          onPlaying={markCurrentStoryVisible}
          onEnded={nextStory}
          onError={() => setMediaError(true)}
          onTimeUpdate={(e) => {
            const el = e.currentTarget
            if (!el.duration || Number.isNaN(el.duration)) return
            setVideoProg(Math.min(1, el.currentTime / el.duration))
          }}
        />
      )
    }

    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        key={`${viewerItem.storyId}:${viewerItem.midiaUrl}`}
        src={viewerItem.midiaUrl}
        alt=""
        className="h-[100svh] w-full max-w-[100vw] object-contain"
        onLoad={markCurrentStoryVisible}
        onError={() => setMediaError(true)}
      />
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} modal={!verificationOpen}>
      <DialogContent className="max-w-none w-screen h-[100svh] p-0 border-0 bg-black">
        <DialogTitle className="sr-only">Visualizar Story</DialogTitle>

        <div
          className={["relative w-full h-full", viewerInteracoesTravadas ? "pointer-events-none" : ""].join(" ")}
          aria-hidden={viewerInteracoesTravadas}
        >
          <div className="absolute top-0 left-0 right-0 z-30 p-4">
            <div className="flex gap-1">
              {progressBars.map((item, idx) => {
                const isDone = idx < itemIndex
                const isActive = idx === itemIndex

                let width = "0%"
                let animate = false

                if (isDone) {
                  width = "100%"
                } else if (isActive && viewerItem?.tipo === "VIDEO" && viewerItem?.viewerState === "LIBERADO") {
                  width = `${Math.round(videoProg * 100)}%`
                } else if (
                  isActive &&
                  (viewerItem?.tipo === "IMAGE" || viewerItem?.modoConteudo === "ANUNCIO") &&
                  viewerItem?.viewerState === "LIBERADO" &&
                  (viewerItem?.modoConteudo === "ANUNCIO" || viewerItem?.midiaUrl)
                ) {
                  animate = true
                }

                return (
                  <div key={String(item.storyId)} className="h-1 flex-1 overflow-hidden rounded bg-white/20">
                    <div
                      className={["h-full bg-white", animate ? "story-progress-fill" : ""].join(" ")}
                      style={{ width, animationDuration: `${IMAGE_MS}ms` }}
                    />
                  </div>
                )
              })}
            </div>

            <div className="mt-3 flex items-center justify-between gap-2">
              {perfilNavegavel && podeNavegarAnuncio ? (
                <button
                  type="button"
                  onClick={irParaAnuncioDoStory}
                  className="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-left hover:bg-white/5 focus:outline-none focus-visible:ring-2 focus-visible:ring-white/40"
                  aria-label={`Ver anuncio de ${rotuloPerfilComIdade}`}
                >
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white/10">
                    {currentBundle?.avatarUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={currentBundle.avatarUrl} className="h-full w-full object-cover" alt="" />
                    ) : (
                      <div className="text-xs font-bold text-white">{getInitials(rotuloPerfil)}</div>
                    )}
                  </div>
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-white hover:underline">
                      {rotuloPerfilComIdade}
                    </div>
                    <div className="text-xs text-white/70">
                      {itemIndex + 1}/{currentBundle?.itens?.length || 0}
                    </div>
                  </div>
                </button>
              ) : (
                <div className="flex min-w-0 flex-1 items-center gap-3 rounded-lg text-left">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white/10">
                    <div className="text-xs font-bold text-white">{getInitials(rotuloPerfilComIdade)}</div>
                  </div>
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold text-white">{rotuloPerfilComIdade}</div>
                    <div className="text-xs text-white/60">
                      {itemIndex + 1}/{currentBundle?.itens?.length || 0}
                    </div>
                  </div>
                </div>
              )}

              <div className="flex shrink-0 items-center gap-2">
                {podeNavegarAnuncio ? (
                  <button
                    type="button"
                    onClick={irParaAnuncioDoStory}
                    className="rounded-full bg-white/10 px-3 py-2 text-xs font-semibold text-white hover:bg-white/20"
                  >
                    Ver anuncio
                  </button>
                ) : null}

                <button
                  onClick={() => onOpenChange(false)}
                  className="h-10 w-10 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center"
                  aria-label="Fechar"
                >
                  <XMarkIcon className="h-6 w-6 text-white" />
                </button>
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={prevStory}
            disabled={isFirst || viewerNavegacaoTravada}
            aria-label="Anterior"
            className={[
              "absolute left-3 top-1/2 -translate-y-1/2 z-30 h-11 w-11 rounded-full",
              "bg-white/10 hover:bg-white/20 flex items-center justify-center transition",
              isFirst || viewerNavegacaoTravada ? "opacity-40 cursor-not-allowed" : "opacity-100",
            ].join(" ")}
          >
            <ChevronLeftIcon className="h-6 w-6 text-white" />
          </button>

          <button
            type="button"
            onClick={nextStory}
            disabled={isLast || viewerNavegacaoTravada}
            aria-label="Proximo"
            className={[
              "absolute right-3 top-1/2 -translate-y-1/2 z-30 h-11 w-11 rounded-full",
              "bg-white/10 hover:bg-white/20 flex items-center justify-center transition",
              isLast || viewerNavegacaoTravada ? "opacity-40 cursor-not-allowed" : "opacity-100",
            ].join(" ")}
          >
            <ChevronRightIcon className="h-6 w-6 text-white" />
          </button>

          <button
            className="absolute left-0 top-0 h-full w-1/3 z-10"
            onClick={prevStory}
            aria-label="Anterior (area)"
            type="button"
            disabled={viewerNavegacaoTravada}
          />
          <button
            className="absolute right-0 top-0 h-full w-1/3 z-10"
            onClick={nextStory}
            aria-label="Proximo (area)"
            type="button"
            disabled={viewerNavegacaoTravada}
          />

          <div className="flex h-full w-full items-center justify-center">{renderViewerBody()}</div>
        </div>

        <VisitorVerificationModal
          open={verificationOpen}
          level="REINFORCED"
          scope="STORY"
          context={{
            anuncioId: viewerItem?.anuncioId ?? undefined,
            route: viewerItem?.anuncioSlug ? `/anuncios/${viewerItem.anuncioSlug}` : "/stories",
            storyId: String(viewerItem?.storyId ?? currentFeedItem?.storyId ?? ""),
          }}
          onOpenChange={setVerificationOpen}
          onVerified={async () => {
            try {
              await refreshViewerAndFeed()
              setVerificationOpen(false)
            } catch {
              toast.error("Nao foi possivel atualizar o acesso agora. Tente novamente.")
            }
          }}
        />

        <style jsx>{`
          @keyframes storyProgress {
            from {
              width: 0%;
            }
            to {
              width: 100%;
            }
          }
          .story-progress-fill {
            width: 0%;
            animation-name: storyProgress;
            animation-timing-function: linear;
            animation-fill-mode: forwards;
          }
        `}</style>
      </DialogContent>
    </Dialog>
  )
}
