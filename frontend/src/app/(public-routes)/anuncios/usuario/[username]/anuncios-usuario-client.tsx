"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { AnuncioCard } from "@/components/anuncios/anuncio-card"
import { useAuth } from "@/context/AuthContext"
import { corrigirEstruturaTexto } from "@/lib/text/encoding"
import type { MidiaPublica } from "@/lib/media/public-media"

type Anuncio = {
  id: number
  slug: string
  titulo: string
  descricao: string
  nomeAnunciante?: string
  usernameAnunciante?: string
  favorito?: boolean
  impulsionado?: boolean
  destaqueAtivo?: boolean
  videoHabilitado?: boolean
  visualizacoes?: number
  preco?: number
  midias?: MidiaPublica[]
  estadoUf?: string | null
  cidadeNome?: string | null
  bairroNome?: string | null
  idade?: number | null
  carrosselDisponivel?: boolean
  whatsappCardEnabled?: boolean
}

export default function AnunciosUsuarioClient({ username }: { username: string }) {
  const { usuario } = useAuth()
  const [anuncios, setAnuncios] = useState<Anuncio[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function run() {
      const u = (username || "").trim()
      if (!u) {
        setAnuncios([])
        setLoading(false)
        return
      }

      setLoading(true)
      setError(null)

      try {
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL}/anuncios/por-usuario/${encodeURIComponent(u)}`,
          { credentials: "include", cache: "no-store" }
        )
        if (!res.ok) throw new Error(`Erro ${res.status}`)
        const data = corrigirEstruturaTexto((await res.json()) as Anuncio[])
        if (!cancelled) setAnuncios(Array.isArray(data) ? data : [])
      } catch {
        if (!cancelled) {
          setError("Não foi possível carregar os anúncios.")
          setAnuncios([])
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [username])

  const login = (username || "").trim()

  if (loading && anuncios.length === 0) {
    return (
      <section className="px-4 py-8">
        <div className="grid grid-cols-1 gap-8 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 6 }).map((_, index) => (
            <div key={index} className="h-[420px] animate-pulse rounded-xl bg-gray-100" />
          ))}
        </div>
      </section>
    )
  }

  if (error) {
    return <div className="px-4 py-10 text-center text-red-500">{error}</div>
  }

  return (
    <section className="space-y-6 px-4 py-8">
      <nav className="text-sm text-gray-600">
        <Link href="/anuncios" className="hover:text-pink-600">
          Anúncios
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">{login ? `@${login}` : "—"}</span>
      </nav>

      <div className="space-y-1">
        <h1 className="text-2xl font-bold text-gray-900">
          Anúncios {login ? <>de @{login}</> : null}
        </h1>
        <p className="text-sm text-gray-600">Perfis ativos publicados por este usuário.</p>
      </div>

      {anuncios.length === 0 ? (
        <p className="text-center text-gray-500">Nenhum anúncio ativo encontrado para este usuário.</p>
      ) : (
        <div className="grid grid-cols-1 gap-8 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
          {anuncios.map((anuncio) => (
            <AnuncioCard
              key={anuncio.id}
              id={anuncio.id}
              slug={anuncio.slug ?? anuncio.titulo.toLowerCase().replace(/\s+/g, "-")}
              usuarioId={usuario?.id}
              nome={anuncio.titulo}
              nomeAnunciante={anuncio.nomeAnunciante}
              usernameAnunciante={anuncio.usernameAnunciante}
              estadoUf={anuncio.estadoUf ?? null}
              cidadeNome={anuncio.cidadeNome ?? null}
              bairroNome={anuncio.bairroNome ?? null}
              idade={anuncio.idade ?? null}
              valor={`A partir de R$ ${anuncio.preco?.toFixed(2) ?? "0,00"} / hora`}
              midias={anuncio.midias ?? []}
              descricao={anuncio.descricao}
              favoritoInicial={anuncio.favorito ?? false}
              destaque={anuncio.destaqueAtivo ?? false}
              visualizacoes={anuncio.visualizacoes ?? 0}
              carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
              videoHabilitado={anuncio.videoHabilitado ?? false}
              whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
            />
          ))}
        </div>
      )}
    </section>
  )
}
