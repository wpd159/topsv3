'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { MessageCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  fetchDashboardTopWhatsapp,
  type AdminDashboardTopWhatsapp,
} from '@/lib/admin-dashboard-api'

const INITIAL_LIMIT = 12
const LIMIT_STEP = 12
const MAX_LIMIT = 48

function errorMessage(error: unknown) {
  return error instanceof Error && error.message
    ? error.message
    : 'Não foi possível carregar o ranking.'
}

export function TopWhatsappHojeCard({ refreshKey }: { refreshKey: number }) {
  const [limit, setLimit] = useState(INITIAL_LIMIT)
  const [data, setData] = useState<AdminDashboardTopWhatsapp | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void fetchDashboardTopWhatsapp(limit, controller.signal)
      .then(setData)
      .catch((loadError) => {
        if (!controller.signal.aborted) setError(errorMessage(loadError))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [limit, refreshKey, retryKey])

  const items = data?.itens ?? []

  return (
    <section className="border border-zinc-200 bg-white">
      <header className="border-b border-zinc-200 px-5 py-4">
        <div className="flex items-center gap-2">
          <MessageCircle className="h-4 w-4 text-emerald-700" aria-hidden />
          <h3 className="text-base font-bold text-zinc-950">
            Mais cliques no WhatsApp hoje
          </h3>
        </div>
        <p className="mt-1 text-xs text-zinc-600">
          Eventos internos permitidos no dia civil do backend.
        </p>
      </header>

      <div className="p-5">
        {loading && !data ? (
          <p className="py-10 text-center text-sm text-zinc-500">Carregando ranking...</p>
        ) : error ? (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <p className="text-sm text-red-700" role="alert">{error}</p>
            <Button type="button" size="sm" variant="outline" onClick={() => setRetryKey((value) => value + 1)}>
              Tentar novamente
            </Button>
          </div>
        ) : items.length === 0 ? (
          <p className="py-10 text-center text-sm text-zinc-500">
            Nenhum clique registrado hoje.
          </p>
        ) : (
          <>
            <div className="grid gap-3 md:grid-cols-2 2xl:grid-cols-3">
              {items.map((item, index) => {
                const location = [item.cidade, item.uf].filter(Boolean).join(' - ')
                return (
                  <article
                    key={item.anuncioId}
                    className="grid min-h-[104px] grid-cols-[64px_1fr] gap-3 border border-zinc-200 p-3"
                  >
                    <div className="h-16 w-16 overflow-hidden bg-zinc-100">
                      {item.miniaturaUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element -- URL pública dinâmica do storage canônico.
                        <img
                          src={item.miniaturaUrl}
                          alt=""
                          className="h-full w-full object-cover"
                          loading="lazy"
                        />
                      ) : (
                        <div className="flex h-full items-center justify-center text-xs text-zinc-400">Sem foto</div>
                      )}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-start justify-between gap-2">
                        <Link
                          href={`/admin/anuncios/${item.anuncioId}`}
                          className="line-clamp-2 text-sm font-semibold text-zinc-950 hover:text-pink-700"
                        >
                          {item.titulo}
                        </Link>
                        <span className="shrink-0 text-xs font-bold text-zinc-500">#{index + 1}</span>
                      </div>
                      <p className="mt-1 truncate text-xs text-zinc-500">{location || 'Local não informado'}</p>
                      <p className="mt-2 text-sm font-bold text-emerald-700">
                        {item.cliquesWhatsappHoje.toLocaleString('pt-BR')} cliques
                      </p>
                      {item.publicado ? (
                        <Link
                          href={`/anuncios/${encodeURIComponent(item.slug)}`}
                          target="_blank"
                          rel="noreferrer"
                          className="text-xs font-medium text-pink-700 hover:underline"
                        >
                          Abrir anúncio público
                        </Link>
                      ) : null}
                    </div>
                  </article>
                )
              })}
            </div>

            <div className="mt-4 flex justify-center gap-2 border-t border-zinc-200 pt-4">
              {data?.temMais && limit < MAX_LIMIT ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={loading}
                  onClick={() => setLimit((value) => Math.min(MAX_LIMIT, value + LIMIT_STEP))}
                >
                  Mostrar mais
                </Button>
              ) : null}
              {limit > INITIAL_LIMIT ? (
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  disabled={loading}
                  onClick={() => setLimit(INITIAL_LIMIT)}
                >
                  Mostrar menos
                </Button>
              ) : null}
            </div>
          </>
        )}
      </div>
    </section>
  )
}
