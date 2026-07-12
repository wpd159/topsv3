'use client'

import { useCallback, useEffect, useState } from 'react'
import { MeuAnuncioCard } from '@/components/anuncios/meu-anuncio-card'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { listarMeusAnuncios, type MeuAnuncio } from '@/lib/meus-anuncios-api'

export default function MeusAnunciosPage() {
  const [anuncios, setAnuncios] = useState<MeuAnuncio[]>([])
  const [loading, setLoading] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  const carregar = useCallback(async () => {
    setLoading(true)
    setErro(null)
    try {
      setAnuncios(await listarMeusAnuncios())
    } catch (error) {
      setErro(error instanceof Error ? error.message : 'Não foi possível carregar seus anúncios.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void carregar()
  }, [carregar])

  return (
    <PainelShell
      title="Meus anúncios"
      description="Consulte o status, a localização e a capa pública dos seus anúncios."
    >
      <div className="space-y-6">
        {!loading && !erro && anuncios.length > 0 ? (
          <section className="rounded-[28px] border border-slate-200 bg-white px-5 py-4 shadow-sm">
            <p className="text-sm font-medium text-slate-600">
              {anuncios.length} {anuncios.length === 1 ? 'anúncio encontrado' : 'anúncios encontrados'}
            </p>
          </section>
        ) : null}

        {loading ? (
          <div className="flex min-h-[240px] items-center justify-center rounded-[28px] border border-slate-200 bg-white text-sm text-slate-500 shadow-sm">
            Carregando seus anúncios...
          </div>
        ) : erro ? (
          <div className="flex min-h-[240px] flex-col items-center justify-center rounded-[28px] border border-rose-200 bg-rose-50 px-6 text-center shadow-sm">
            <p className="font-semibold text-rose-900">Não foi possível carregar seus anúncios.</p>
            <p className="mt-2 max-w-lg text-sm leading-6 text-rose-800">{erro}</p>
            <button
              type="button"
              onClick={() => void carregar()}
              className="mt-5 rounded-lg bg-[#FC1EAD] px-5 py-2.5 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-[#e01a9a] hover:shadow-sm"
            >
              Tentar novamente
            </button>
          </div>
        ) : anuncios.length === 0 ? (
          <div className="flex min-h-[260px] flex-col items-center justify-center rounded-[28px] border border-dashed border-slate-300 bg-white px-6 text-center shadow-sm">
            <p className="text-lg font-semibold text-slate-800">Você ainda não possui anúncios.</p>
            <p className="mt-2 max-w-md text-sm leading-6 text-slate-500">
              Quando houver anúncios vinculados à sua conta, eles aparecerão aqui.
            </p>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
            {anuncios.map((anuncio) => (
              <MeuAnuncioCard key={anuncio.id} anuncio={anuncio} />
            ))}
          </div>
        )}
      </div>
    </PainelShell>
  )
}
