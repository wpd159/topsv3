import Link from "next/link"
import type { ReactNode } from "react"
import type { PublicCatalogList } from "@/lib/public-catalog-api"
import { buildPublicPageHref } from "@/lib/seo/public-url"
import AnuncioCard from "./anuncio-card"

interface ListagemPublicaPaginadaProps {
  caminhoBase: string
  initialData: PublicCatalogList
  searchParams?: Record<string, string | string[] | undefined>
  children?: ReactNode
}

export function ListagemPublicaPaginada({
  caminhoBase,
  initialData,
  searchParams,
  children,
}: ListagemPublicaPaginadaProps) {
  const data = initialData
  const pagina = data.paginacao.pagina
  const ordemSeed = initialData.paginacao.ordemSeed

  return (
    <>
      {data.itens.length > 0 && <h2 className="sr-only">Anúncios nesta página</h2>}
      <div className="grid scroll-mt-24 grid-cols-1 gap-4 transition-opacity md:grid-cols-2 lg:grid-cols-4 opacity-100">
        {data.itens.map((anuncio, index) => (
          <AnuncioCard
            key={anuncio.id}
            id={anuncio.id}
            slug={anuncio.slug}
            nome={anuncio.titulo}
            idade={anuncio.idade}
            estadoUf={anuncio.estadoUf ?? null}
            cidadeNome={anuncio.cidadeNome ?? null}
            bairroNome={anuncio.bairroNome ?? null}
            valor={`A partir de R$ ${Number(anuncio.preco ?? 0).toFixed(2)} / hora`}
            midias={anuncio.midias ?? []}
            descricao={anuncio.descricao}
            destaque={anuncio.destaqueAtivo ?? false}
            visualizacoes={anuncio.visualizacoes}
            anunciaDesde={anuncio.anunciaDesde ?? null}
            carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
            videoHabilitado={anuncio.videoHabilitado ?? false}
            whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
            comLocal={anuncio.comLocal}
            fazAnal={anuncio.fazAnal}
            mediaPriority={index === 0}
          />
        ))}
      </div>

      {children}

      {data.paginacao.totalPaginas > 1 && (
        <nav className="flex items-center justify-center gap-2 border-t py-8" aria-label="Paginacao">
          {pagina > 0 && (
            <Link
              href={buildPublicPageHref(caminhoBase, pagina - 1, ordemSeed, searchParams)}
              prefetch={false}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Anterior
            </Link>
          )}

          <div className="flex gap-1">
            {Array.from({ length: Math.min(data.paginacao.totalPaginas, 5) }).map((_, index) => (
              <Link
                key={index}
                href={buildPublicPageHref(caminhoBase, index, ordemSeed, searchParams)}
                prefetch={false}
                aria-current={pagina === index ? "page" : undefined}
                className={`rounded-lg px-3 py-2 ${
                  pagina === index
                    ? "bg-pink-600 text-white"
                    : "border border-gray-300 hover:bg-gray-100"
                }`}
              >
                {index + 1}
              </Link>
            ))}
          </div>

          {pagina < data.paginacao.totalPaginas - 1 && (
            <Link
              href={buildPublicPageHref(caminhoBase, pagina + 1, ordemSeed, searchParams)}
              prefetch={false}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Proxima
            </Link>
          )}
        </nav>
      )}
    </>
  )
}
