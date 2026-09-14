'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

// Only pass an authenticated management response or a locally created object URL.
// Private signatures remain opaque; this path never uses the public image optimizer.
function fontePrivada(value: string | null | undefined) {
  if (!value || value.includes('\\') || /[\u0000-\u001f]/.test(value)) return null
  try {
    const url = new URL(value, 'https://local.invalid')
    if (url.username || url.password) return null
    if (value.startsWith('/') && !value.startsWith('//')) return value
    if (url.protocol === 'https:') return value
    if (url.protocol === 'blob:' && typeof window !== 'undefined'
      && new URL(url.pathname).origin === window.location.origin) return value
  } catch { /* Invalid sources are never requested. */ }
  return null
}

export function ImagemProprietario({
  src,
  expiresAt,
  alt,
  className = '',
  onRefresh,
  interactive = true,
}: {
  src: string | null | undefined
  expiresAt?: string | null
  alt: string
  className?: string
  onRefresh?: () => Promise<void>
  interactive?: boolean
}) {
  const identity = JSON.stringify([src, expiresAt])
  const [failedIdentity, setFailedIdentity] = useState<string | null>(null)
  const [expiredIdentity, setExpiredIdentity] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const refreshRef = useRef(onRefresh)
  refreshRef.current = onRefresh
  const inFlightRef = useRef(false)
  const mountedRef = useRef(true)
  const automaticErrorRefreshRef = useRef(false)
  const expiryAttemptRef = useRef<string | null>(null)
  const expiration = expiresAt == null ? null : Date.parse(expiresAt)
  const expired = expiration !== null && (!Number.isFinite(expiration) || expiration <= Date.now())
  const source = fontePrivada(src)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  const renew = useCallback(async (requestIdentity: string) => {
    if (!refreshRef.current || inFlightRef.current) return
    inFlightRef.current = true
    setRefreshing(true)
    try {
      await refreshRef.current()
      if (mountedRef.current) setFailedIdentity((current) => current === requestIdentity ? null : current)
    } catch {
      if (mountedRef.current) setFailedIdentity(requestIdentity)
    } finally {
      inFlightRef.current = false
      if (mountedRef.current) setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    if (!src || expiresAt == null) return
    const timestamp = Date.parse(expiresAt)
    const expire = () => {
      setExpiredIdentity(identity)
      if (expiryAttemptRef.current === identity) return
      expiryAttemptRef.current = identity
      void renew(identity)
    }
    const check = () => {
      if (!Number.isFinite(timestamp) || timestamp <= Date.now()) expire()
    }
    const timer = window.setTimeout(expire, Number.isFinite(timestamp)
      ? Math.min(Math.max(0, timestamp - Date.now()), 2_147_483_647) : 0)
    window.addEventListener('focus', check)
    document.addEventListener('visibilitychange', check)
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('focus', check)
      document.removeEventListener('visibilitychange', check)
    }
  }, [expiresAt, identity, renew, src])

  if (!source || expired || expiredIdentity === identity || failedIdentity === identity) {
    return (
      <div className={`flex h-full w-full flex-col items-center justify-center gap-2 bg-slate-100 p-3 text-center text-xs text-slate-600 ${className}`}>
        <span>{refreshing ? 'Atualizando foto…' : source ? 'Não foi possível carregar esta foto.' : 'Nenhuma foto disponível.'}</span>
        {interactive && onRefresh && source ? (
          <button type="button" disabled={refreshing} className="rounded border border-slate-300 bg-white px-3 py-2" onClick={() => void renew(identity)}>
            Atualizar foto
          </button>
        ) : null}
      </div>
    )
  }

  return (
    <img
      key={identity}
      src={source}
      alt={alt}
      referrerPolicy="no-referrer"
      className={`h-full w-full object-cover object-center ${className}`}
      onError={() => {
        setFailedIdentity(identity)
        if (automaticErrorRefreshRef.current) return
        automaticErrorRefreshRef.current = true
        void renew(identity)
      }}
    />
  )
}
