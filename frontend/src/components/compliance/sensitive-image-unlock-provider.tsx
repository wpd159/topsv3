"use client"

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react"

type UnlockMap = Record<string, boolean>

type SensitiveImageUnlockContextValue = {
  isUnlocked: (key: string | null) => boolean
  unlock: (key: string | null) => void
  lock: (key: string | null) => void
}
const SensitiveImageUnlockContext = createContext<SensitiveImageUnlockContextValue | null>(null)

export function resolverChaveUnlockAnuncio({
  anuncioId,
  anuncioSlug,
}: {
  anuncioId?: string | number | null
  anuncioSlug?: string | null
}) {
  if (typeof anuncioId === "number" && Number.isFinite(anuncioId)) {
    return `anuncio:${anuncioId}`
  }

  if (typeof anuncioId === "string" && anuncioId.trim()) {
    return `anuncio:${anuncioId.trim()}`
  }

  const slug = (anuncioSlug ?? "").trim()
  if (slug) return `slug:${slug}`

  return null
}

export function SensitiveImageUnlockProvider({ children }: { children: ReactNode }) {
  const [unlocks, setUnlocks] = useState<UnlockMap>({})

  const isUnlocked = useCallback(
    (key: string | null) => {
      if (!key) return false
      return Boolean(unlocks[key])
    },
    [unlocks]
  )

  const unlock = useCallback((key: string | null) => {
    if (!key) return
    setUnlocks((prev) => {
      if (prev[key]) return prev
      return { ...prev, [key]: true }
    })
  }, [])

  const lock = useCallback((key: string | null) => {
    if (!key) return
    setUnlocks((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }, [])

  const value = useMemo(
    () => ({
      isUnlocked,
      unlock,
      lock,
    }),
    [isUnlocked, unlock, lock]
  )

  return (
    <SensitiveImageUnlockContext.Provider value={value}>
      {children}
    </SensitiveImageUnlockContext.Provider>
  )
}

export function useSensitiveImageUnlockContext() {
  const context = useContext(SensitiveImageUnlockContext)
  if (!context) {
    throw new Error("useSensitiveImageUnlockContext deve ser usado dentro de SensitiveImageUnlockProvider.")
  }
  return context
}
