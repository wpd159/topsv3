'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { ChatBubbleOvalLeftEllipsisIcon } from '@heroicons/react/24/outline'
import { cn } from '@/lib/utils'
import { fetchTopWhatsappHoje, type TopWhatsappHojeItem } from '@/lib/admin-estatisticas-api'
import { Button } from '@/components/ui/button'

const INITIAL_LIMIT = 12
const LIMIT_STEP = 12
const MAX_LIMIT = 48

function fmt(n: number) {
  return Number(n).toLocaleString('pt-BR')
}

export function TopWhatsappHojeCard() {
  const [limit, setLimit] = useState(INITIAL_LIMIT)
  const [items, setItems] = useState<TopWhatsappHojeItem[]>([])
  const [loading, setLoading] = useState(true)
  const [fetching, setFetching] = useState(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setFetching(true)
      try {
        const rows = await fetchTopWhatsappHoje(limit)
        if (!cancelled) setItems(rows ?? [])
      } finally {
        if (!cancelled) {
          setFetching(false)
          setLoading(false)
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [limit])

  /** Há possivelmente mais linhas no servidor se a API encheu o `limit` pedido. */
  const hasMore = limit < MAX_LIMIT && items.length >= limit
  const canShowLess = limit > INITIAL_LIMIT

  return (
    <div className="overflow-hidden rounded-2xl border border-gray-200/80 bg-white shadow-sm">
      <div className="border-b border-gray-100 px-5 py-4">
        <div className="flex items-center gap-2">
          <ChatBubbleOvalLeftEllipsisIcon className="h-5 w-5 text-emerald-600" aria-hidden />
          <h3 className="text-base font-bold text-gray-900">Anúncios com mais cliques no WhatsApp hoje</h3>
        </div>
        <p className="mt-0.5 text-xs text-gray-500">
          Contagem do dia civil atual (registros em{' '}
          <code className="rounded bg-gray-100 px-1 text-[10px]">cliques_whatsapp</code>).
        </p>
      </div>

      <div className="px-5 pb-5 pt-4">
        {loading && items.length === 0 ? (
          <p className="py-12 text-center text-sm text-gray-500">Carregando…</p>
        ) : items.length === 0 ? (
          <p className="py-12 text-center text-sm text-gray-500">Nenhum clique registrado hoje.</p>
        ) : (
          <>
            <div
              className={cn(
                'grid gap-3',
                'grid-cols-1 md:grid-cols-2 2xl:grid-cols-3',
                fetching && 'pointer-events-none opacity-70'
              )}
            >
              {items.map((item, i) => {
                const rank = i + 1
                const modHref = `/admin/moderacao-v2/${item.anuncioId}`
                const pubHref =
                  item.slug && String(item.slug).trim() !== ''
                    ? `/anuncios/${encodeURIComponent(item.slug)}`
                    : null
                const thumb = item.thumbnailUrl?.trim()
                const isTop = rank === 1

                return (
                  <article
                    key={item.anuncioId}
                    className={cn(
                      'flex gap-3 overflow-hidden rounded-xl border bg-white p-3 shadow-sm transition',
                      isTop
                        ? 'border-emerald-300/80 ring-1 ring-emerald-100/90'
                        : 'border-gray-200/90 hover:border-gray-300/90 hover:shadow'
                    )}
                  >
                    <div className="relative h-[72px] w-[72px] shrink-0 overflow-hidden rounded-lg bg-gray-100 ring-1 ring-black/5">
                      {thumb ? (
                        // eslint-disable-next-line @next/next/no-img-element -- URL dinâmica do storage (vários hosts)
                        <img
                          src={thumb}
                          alt=""
                          className="h-full w-full object-cover"
                          loading="lazy"
                          decoding="async"
                        />
                      ) : (
                        <div className="flex h-full w-full items-center justify-center text-[10px] text-gray-400">
                          —
                        </div>
                      )}
                    </div>

                    <div className="flex min-w-0 flex-1 flex-col gap-1">
                      <span
                        className={cn(
                          'inline-flex w-fit rounded-md px-1.5 py-0.5 text-[10px] font-bold tabular-nums tracking-wide',
                          isTop ? 'bg-emerald-600 text-white' : 'bg-gray-100 text-gray-700'
                        )}
                      >
                        #{rank}
                      </span>
                      <h4 className="line-clamp-2 text-sm font-semibold leading-snug text-gray-900">
                        <Link href={modHref} className="hover:text-[#f0198f]">
                          {item.titulo?.trim() || `Anúncio #${item.anuncioId}`}
                        </Link>
                      </h4>
                      <p className="line-clamp-1 text-xs text-gray-500">{(item.cidadeNome ?? '—').toString()}</p>

                      <div className="mt-0.5 flex flex-wrap items-end gap-x-2 gap-y-0">
                        <span className="text-xl font-bold tabular-nums leading-none text-emerald-700">
                          {fmt(item.cliquesWhatsappHoje)}
                        </span>
                        <span className="pb-0.5 text-[9px] font-medium uppercase tracking-wide text-gray-400">
                          cliques hoje
                        </span>
                      </div>

                      <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[10px]">
                        <Link href={modHref} className="font-medium text-[#f0198f] hover:underline">
                          Moderação v2
                        </Link>
                        {pubHref ? (
                          <Link href={pubHref} className="text-gray-600 hover:underline" target="_blank" rel="noreferrer">
                            Ver anúncio
                          </Link>
                        ) : (
                          <span className="text-gray-400">Sem slug</span>
                        )}
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>

            {(hasMore || canShowLess) && (
              <div className="mt-4 flex flex-wrap items-center justify-center gap-2 border-t border-gray-100 pt-4">
                {hasMore ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={fetching}
                    onClick={() => setLimit((l) => Math.min(MAX_LIMIT, l + LIMIT_STEP))}
                  >
                    Mostrar mais
                  </Button>
                ) : null}
                {canShowLess ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={fetching}
                    onClick={() => setLimit((l) => Math.max(INITIAL_LIMIT, l - LIMIT_STEP))}
                  >
                    Mostrar menos
                  </Button>
                ) : null}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
