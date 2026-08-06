"use client"

import { useCallback, useEffect, useState } from "react"
import { PlayCircleIcon } from "@heroicons/react/24/solid"

import type { StoryBundle, StoryItem } from "./stories-types"
import {
  getInitials,
  isExpired,
  loginPublicoDoBundle,
  rotuloPublicoComIdade,
  rotuloPublicoDoBundle,
} from "./stories-types"
import { StoryViewerDialog } from "./story-viewer-dialog"
import { publicApiUrl } from '@/lib/api-contract'
import { useAuth } from '@/context/AuthContext'
import { sanitizeStoryFeedItem } from './story-access-policy'
import { markStorySeen, orderStoryBundles, readStoryState, storyStorageKey } from './story-ordering'

function browserStorage() {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function previewExigeBloqueio(item?: StoryItem) {
  if (!item) return false
  return item.previewState === "IDADE_NAO_CONFIRMADA"
}

function StoryPreviewAvatar({ bundle, first }: { bundle: StoryBundle; first?: StoryItem }) {
  const rotuloPerfil = rotuloPublicoDoBundle(bundle)
  const previewBloqueado = previewExigeBloqueio(first)

  if (previewBloqueado && first?.tipo === "IMAGE" && first.previewUrl) {
    return (
      <div className="absolute inset-0">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={first.previewUrl}
          alt={`Foto de perfil de ${rotuloPerfil}`}
          width={64}
          height={64}
          className="h-full w-full object-cover"
        />
        <span className="absolute bottom-1 right-1 rounded bg-black/75 px-1.5 py-0.5 text-[9px] font-bold text-white">
          18+
        </span>
      </div>
    )
  }

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
      <img
        src={first.previewUrl}
        alt={`Foto de perfil de ${rotuloPerfil}`}
        width={64}
        height={64}
        className="h-full w-full object-cover"
      />
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
  const { usuario } = useAuth()
  const [loading, setLoading] = useState(false)
  const [bundles, setBundles] = useState<StoryBundle[]>([])
  const [indisponivel, setIndisponivel] = useState(false)
  const [openViewer, setOpenViewer] = useState(false)
  const [viewerStartIndex, setViewerStartIndex] = useState(0)
  const audience = usuario?.id ? `user:${usuario.id}` : 'visitor'

  const ordered = useCallback((items: StoryBundle[]) => {
    const state = readStoryState(browserStorage(), audience, items)
    return orderStoryBundles(items, state) as StoryBundle[]
  }, [audience])

  const fetchStories = useCallback(async () => {
    try {
      setLoading(true)
      setIndisponivel(false)
      const res = await fetch(publicApiUrl('/stories/ativos'), { credentials: "include", cache: "no-store" })
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
            .map((item: StoryItem) => sanitizeStoryFeedItem(item) as StoryItem),
        }))
        .filter((bundle) => (bundle?.itens || []).length > 0)

      setBundles(ordered(sanitized))
    } catch {
      setIndisponivel(true)
      setBundles([])
    } finally {
      setLoading(false)
    }
  }, [ordered])

  useEffect(() => {
    void fetchStories()
    const t = setInterval(() => void fetchStories(), 60_000)
    return () => clearInterval(t)
  }, [fetchStories])

  useEffect(() => {
    const prefix = storyStorageKey(audience)
    const synchronize = (event: StorageEvent) => {
      if (event.key?.startsWith(prefix)) {
        setBundles((current) => ordered(current))
      }
    }
    window.addEventListener('storage', synchronize)
    return () => window.removeEventListener('storage', synchronize)
  }, [audience, ordered])

  function openBundleAt(i: number) {
    setViewerStartIndex(i)
    setOpenViewer(true)
  }

  const handleViewerOpenChange = useCallback((open: boolean) => {
    setOpenViewer(open)
    if (!open) setBundles((current) => ordered(current))
  }, [ordered])

  const handleStoryCurrent = useCallback((item: StoryItem) => {
    markStorySeen(browserStorage(), audience, item, bundles)
  }, [audience, bundles])

  return (
    <>
      <div className="w-full py-2">
        <div className="flex items-center justify-between">
          <div className="text-lg font-semibold leading-tight text-gray-900">Stories</div>
          {loading && <div className="text-xs text-gray-500">Atualizando...</div>}
        </div>

        {indisponivel && (
          <p className="mt-3 text-sm text-gray-500" role="status">
            Stories indisponíveis no momento.
          </p>
        )}

        {!indisponivel && <div className="mt-4 flex gap-3 overflow-x-auto pb-3">
          {bundles.map((bundle, index) => {
            const login = loginPublicoDoBundle(bundle)
            const rotuloPerfil = rotuloPublicoDoBundle(bundle)
            const primeiro = bundle.itens?.[0]
            const rotuloComIdade = rotuloPublicoComIdade(rotuloPerfil, primeiro?.idade ?? bundle.idade)
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
              <div key={String(primeiro?.storyId ?? index)} className="shrink-0 flex flex-col items-center gap-2">
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

                <span
                  className="max-w-[92px] truncate text-[11px] text-gray-700"
                  title={rotuloComIdade}
                >
                  {rotuloComIdade}
                </span>
              </div>
            )
          })}
        </div>}
      </div>

      <StoryViewerDialog
        open={openViewer}
        onOpenChange={handleViewerOpenChange}
        bundles={bundles}
        initialBundleIndex={viewerStartIndex}
        onVerificationRefresh={fetchStories}
        onStoryCurrent={handleStoryCurrent}
      />
    </>
  )
}
