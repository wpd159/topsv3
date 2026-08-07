'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'

import { AnuncioCard } from '@/components/anuncios/anuncio-card'
import { ContractState } from '@/components/feedback/contract-state'
import { Button } from '@/components/ui/button'
import {
  listarAnunciosPublicosPorUsuario,
  type PublicCatalogCard,
} from '@/lib/public-catalog-api'

const ITENS_POR_PAGINA = 20

export default function AnunciosUsuarioClient({ publicUsername }: { publicUsername: string }) {
  const username = publicUsername.trim()
  const [displayUsername, setDisplayUsername] = useState('')
  const [anuncios, setAnuncios] = useState<PublicCatalogCard[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [ordemSeed, setOrdemSeed] = useState<string | null>(null)
  const [proximaPagina, setProximaPagina] = useState<number | null>(null)
  const [reloadMarker, setReloadMarker] = useState(0)
  const consultaRef = useRef(0)

  useEffect(() => {
    const consulta = consultaRef.current + 1
    consultaRef.current = consulta
    let ativo = true

    setDisplayUsername('')
    if (!username) {
      setAnuncios([])
      setError(null)
      setLoading(false)
      return () => {
        ativo = false
      }
    }

    setLoading(true)
    setError(null)
    void listarAnunciosPublicosPorUsuario(username, 0, ITENS_POR_PAGINA)
      .then((data) => {
        if (!ativo || consulta !== consultaRef.current) return
        setAnuncios(data.itens)
        setDisplayUsername(data.displayUsername?.trim() ?? '')
        setOrdemSeed(data.paginacao.ordemSeed)
        setProximaPagina(
          data.paginacao.pagina + 1 < data.paginacao.totalPaginas
            ? data.paginacao.pagina + 1
            : null,
        )
      })
      .catch((requestError) => {
        if (!ativo || consulta !== consultaRef.current) return
        setAnuncios([])
        setError(requestError)
      })
      .finally(() => {
        if (ativo && consulta === consultaRef.current) setLoading(false)
      })

    return () => {
      ativo = false
    }
  }, [username, reloadMarker])

  const carregarMais = useCallback(async () => {
    if (!username || loading || proximaPagina == null || ordemSeed == null) return
    const consulta = consultaRef.current
    setLoading(true)
    setError(null)
    try {
      const data = await listarAnunciosPublicosPorUsuario(
        username,
        proximaPagina,
        ITENS_POR_PAGINA,
        ordemSeed,
      )
      if (consulta !== consultaRef.current) return
      if (data.paginacao.ordemSeed !== ordemSeed) {
        throw new Error('A ordenação da listagem mudou durante a paginação.')
      }
      setAnuncios((atuais) => {
        const ids = new Set(atuais.map((anuncio) => anuncio.id))
        return [...atuais, ...data.itens.filter((anuncio) => !ids.has(anuncio.id))]
      })
      setProximaPagina(
        data.paginacao.pagina + 1 < data.paginacao.totalPaginas
          ? data.paginacao.pagina + 1
          : null,
      )
    } catch (requestError) {
      if (consulta === consultaRef.current) setError(requestError)
    } finally {
      if (consulta === consultaRef.current) setLoading(false)
    }
  }, [loading, ordemSeed, proximaPagina, username])

  return (
    <main className="mx-auto w-full max-w-7xl space-y-7 px-4 py-8 sm:px-6 lg:px-8">
      <nav className="flex flex-wrap items-center gap-2 text-sm text-gray-600" aria-label="Navegação estrutural">
        <Button
          type="button"
          variant="ghost"
          className="h-auto p-0 text-sm"
          onClick={() => window.history.back()}
        >
          Voltar
        </Button>
        <span aria-hidden="true">/</span>
        <Link href="/anuncios" className="hover:text-pink-600">
          Anúncios
        </Link>
        <span aria-hidden="true">/</span>
        <span className="min-w-0 break-all font-medium text-gray-900">
          {displayUsername ? `@${displayUsername}` : 'Anunciante'}
        </span>
      </nav>

      <header className="space-y-1">
        <h1 className="break-words text-2xl font-bold text-gray-900 sm:text-3xl">
          Anúncios {displayUsername ? <>de @{displayUsername}</> : null}
        </h1>
        <p className="text-sm text-gray-600">Anúncios públicos disponíveis desta anunciante.</p>
      </header>

      {loading && anuncios.length === 0 ? (
        <section className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4" aria-label="Carregando anúncios">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="h-[420px] animate-pulse rounded-lg bg-gray-100" />
          ))}
        </section>
      ) : null}

      {error && anuncios.length === 0 ? (
        <ContractState error={error} onRetry={() => setReloadMarker((value) => value + 1)} />
      ) : null}

      {!loading && !error && anuncios.length === 0 ? (
        <section className="rounded-lg border border-gray-200 bg-white px-5 py-12 text-center" role="status">
          <p className="text-sm font-medium text-gray-700">
            Nenhum anúncio público disponível no momento.
          </p>
        </section>
      ) : null}

      {anuncios.length > 0 ? (
        <section className="space-y-6" aria-busy={loading} aria-label="Anúncios da anunciante">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {anuncios.map((anuncio, index) => (
              <AnuncioCard
                key={anuncio.id}
                id={anuncio.id}
                slug={anuncio.slug}
                nome={anuncio.titulo}
                idade={anuncio.idade}
                estadoUf={anuncio.estadoUf}
                cidadeNome={anuncio.cidadeNome}
                bairroNome={anuncio.bairroNome}
                pontoReferenciaTexto={anuncio.enderecoResumido}
                valor={`A partir de R$ ${anuncio.preco?.toFixed(2) ?? '0,00'} / hora`}
                midias={anuncio.midias}
                descricao={anuncio.descricao}
                destaque={anuncio.destaqueAtivo}
                visualizacoes={anuncio.visualizacoes}
                anunciaDesde={anuncio.anunciaDesde}
                carrosselDisponivel={anuncio.carrosselDisponivel}
                videoHabilitado={anuncio.videoHabilitado}
                whatsappCardEnabled={anuncio.whatsappCardEnabled}
                comLocal={anuncio.comLocal}
                fazAnal={anuncio.fazAnal}
                mediaPriority={index === 0}
                onAccessUpdated={() => setReloadMarker((value) => value + 1)}
              />
            ))}
          </div>

          {error ? <ContractState error={error} onRetry={() => void carregarMais()} compact /> : null}

          {proximaPagina != null ? (
            <div className="flex justify-center">
              <Button type="button" variant="outline" disabled={loading} onClick={() => void carregarMais()}>
                {loading ? 'Carregando...' : 'Ver mais resultados'}
              </Button>
            </div>
          ) : null}
        </section>
      ) : null}

      <div className="flex flex-wrap gap-3">
        <Button asChild variant="outline">
          <Link href="/anuncios">Ver catálogo completo</Link>
        </Button>
        {displayUsername ? (
          <Button asChild>
            <Link href={`/chat?usuario=${encodeURIComponent(displayUsername)}`}>Conversar</Link>
          </Button>
        ) : null}
      </div>
    </main>
  )
}
