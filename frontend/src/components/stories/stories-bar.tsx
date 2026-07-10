"use client"

import { useEffect, useState } from "react"
import { toast } from "sonner"
import { PlayCircleIcon } from "@heroicons/react/24/solid"

import type { StoryBundle, StoryItem } from "./stories-types"
import { getInitials, isExpired, loginPublicoDoBundle, rotuloPublicoDoBundle } from "./stories-types"
import { StoryViewerDialog } from "./story-viewer-dialog"

function shuffleBundles<T>(list: T[]): T[] {
  const next = [...list]
  for (let i = next.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[next[i], next[j]] = [next[j], next[i]]
  }
  return next
}

function previewExigeBloqueio(item?: StoryItem) {
  if (!item) return false
  return item.previewState === "IDADE_NAO_CONFIRMADA"
}

function StoryPreviewAvatar({ bundle, first }: { bundle: StoryBundle; first?: StoryItem }) {
  const rotuloPerfil = rotuloPublicoDoBundle(bundle)
  const previewBloqueado = previewExigeBloqueio(first)

  if (previewBloqueado) {
    return (
      <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-gray-900 via-gray-800 to-black text-[10px] font-bold uppercase tracking-wide text-white">
        18+
      </div>
    )
  }

  if (first?.previewState === "AVAILABLE" && first.tipo === "IMAGE" && first.previewUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={first.previewUrl} alt="" className="h-full w-full object-cover" />
    )
  }

  if (first?.previewState === "AVAILABLE" && first.tipo === "VIDEO") {
    return (
      <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-slate-900 via-slate-800 to-black text-white">
        <PlayCircleIcon className="h-8 w-8" />
      </div>
    )
  }

  if (bundle.avatarUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={bundle.avatarUrl} alt="" className="h-full w-full object-cover" />
    )
  }

  return (
    <div className="flex h-full w-full items-center justify-center bg-gray-100 text-sm font-bold text-gray-700">
      {getInitials(rotuloPerfil)}
    </div>
  )
}

export function StoriesBar() {
  const API = process.env.NEXT_PUBLIC_API_URL!

  const [loading, setLoading] = useState(false)
  const [bundles, setBundles] = useState<StoryBundle[]>([])
  const [openViewer, setOpenViewer] = useState(false)
  const [viewerStartIndex, setViewerStartIndex] = useState(0)

  async function fetchStories(options?: { preserveOrder?: boolean }) {
    try {
      setLoading(true)
      const res = await fetch(`${API}/stories/ativos`, { credentials: "include", cache: "no-store" })
      const data = await res.json().catch(() => null)
      if (!res.ok) {
        throw new Error(data?.error || data?.message || "Falha ao carregar stories.")
      }

      const list: StoryBundle[] = Array.isArray(data) ? data : data?.items || []
      const sanitized = (list || [])
        .map((bundle) => ({
          ...bundle,
          itens: (bundle?.itens || [])
            .filter((item: StoryItem) => !isExpired(item))
            .map((item: StoryItem) => {
              const previewBloqueado = previewExigeBloqueio(item)
              const previewDisponivel = !previewBloqueado && item.previewState === "AVAILABLE"

              return {
                ...item,
                previewUrl: previewDisponivel ? item.previewUrl ?? null : null,
              }
            }),
        }))
        .filter((bundle) => (bundle?.itens || []).length > 0)

      setBundles(options?.preserveOrder ? sanitized : shuffleBundles(sanitized))
    } catch (e: any) {
      toast.error(e?.message || "Erro ao carregar stories.")
      setBundles([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void fetchStories()
    const t = setInterval(() => void fetchStories(), 60_000)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function openBundleAt(i: number) {
    setViewerStartIndex(i)
    setOpenViewer(true)
  }

  return (
    <>
      <div className="w-full">
        <div className="flex items-center justify-between">
          <div className="text-sm font-semibold text-gray-900">Stories</div>
          {loading && <div className="text-xs text-gray-500">Atualizando...</div>}
        </div>

        <div className="mt-3 flex gap-3 overflow-x-auto pb-2">
          {bundles.map((bundle, index) => {
            const login = loginPublicoDoBundle(bundle)
            const rotuloPerfil = rotuloPublicoDoBundle(bundle)
            const primeiro = bundle.itens?.[0]
            const pulse = bundle.visto ? "" : "animate-pulse"
            const border = bundle.visto
              ? "bg-gray-200"
              : "bg-gradient-to-tr from-[#FC1EAD] via-[#ff4fd0] to-[#ff7ac8]"

            const avatar = (
              <div className={`rounded-full ${border} p-[2px] ${pulse}`}>
                <div className="relative flex h-16 w-16 items-center justify-center overflow-hidden rounded-full bg-white">
                  <StoryPreviewAvatar bundle={bundle} first={primeiro} />
                </div>
              </div>
            )

            return (
              <div key={String(bundle.usuarioId)} className="shrink-0 flex flex-col items-center gap-2">
                <div className="relative">
                  <button
                    onClick={() => openBundleAt(index)}
                    className="flex cursor-pointer flex-col items-center gap-2"
                    type="button"
                    title={login ? `Assistir stories de @${login}` : "Assistir stories"}
                  >
                    {avatar}
                  </button>

                  <button
                    type="button"
                    className="absolute -bottom-0.5 -right-0.5 z-10 flex h-7 w-7 items-center justify-center rounded-full border border-white bg-black/55 text-white shadow hover:bg-black/70"
                    aria-label="Assistir stories"
                    title="Assistir stories"
                    onClick={(e) => {
                      e.preventDefault()
                      e.stopPropagation()
                      openBundleAt(index)
                    }}
                  >
                    <PlayCircleIcon className="h-5 w-5" />
                  </button>
                </div>

                <span className="max-w-[72px] truncate text-[11px] text-gray-700">{rotuloPerfil}</span>
              </div>
            )
          })}
        </div>
      </div>

      <StoryViewerDialog
        open={openViewer}
        onOpenChange={setOpenViewer}
        bundles={bundles}
        initialBundleIndex={viewerStartIndex}
        onVerificationRefresh={() => fetchStories({ preserveOrder: true })}
      />
    </>
  )
}
