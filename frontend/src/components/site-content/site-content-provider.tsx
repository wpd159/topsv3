'use client'

import { createContext, useContext, type ReactNode } from 'react'
import type { SiteContentEntry, SiteContentKey } from '@/lib/site-content'

const SiteContentContext = createContext<ReadonlyMap<SiteContentKey, SiteContentEntry> | null>(
  null,
)

export function SiteContentProvider({
  entries,
  children,
}: {
  entries: SiteContentEntry[]
  children: ReactNode
}) {
  const byKey = new Map(entries.map((entry) => [entry.contentKey, entry]))
  return <SiteContentContext.Provider value={byKey}>{children}</SiteContentContext.Provider>
}

export function useSiteContent(contentKey: SiteContentKey) {
  const context = useContext(SiteContentContext)
  const entry = context?.get(contentKey)
  if (!entry) {
    throw new Error('Conteudo institucional nao foi fornecido pelo servidor.')
  }
  return entry
}
