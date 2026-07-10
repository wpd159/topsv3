import { Metadata } from "next"
import { notFound } from "next/navigation"
import Link from "next/link"
import {
  gerarConteudoSeoBairro,
  gerarBreadcrumbSchemaBairro,
} from "@/lib/seo/seoContentGeneratorBairro"
import AnuncioCard from "@/components/anuncios/anuncio-card"
import { StoriesBar } from "@/components/stories/stories-bar"
import { serverApiFetchJson } from "@/lib/server-api"
import { selecionarCapaPublicaSegura, type MidiaPublica } from "@/lib/media/public-media"
import {
  labelAcompanhantesBairro,
  labelAcompanhantesCidade,
} from "@/lib/seo/local-labels"
import { isBairroIndexavelLocal } from "@/lib/seo/local-indexing"
import { gerarFaqSchema } from "@/lib/seo/programmatic-content"
import {
  buildPublicPath,
  buildPublicUrl,
  getPublicSiteBaseUrl,
  parsePublicPage,
} from "@/lib/seo/public-url"

export const revalidate = 3600

interface PageProps {
  params: Promise<{
    estado: string
    cidade: string
    bairro: string
  }>
  searchParams: Promise<{
    page?: string
  }>
}

interface AnuncioSeoDTO {
  id: number
  slug: string
  titulo: string
  preco?: number
  midias?: MidiaPublica[]
  descricao?: string
  nomeAnunciante?: string
  usernameAnunciante?: string
  visualizacoes?: number
  cidadeNome?: string
  bairroNome?: string
  estadoUf?: string
  estadoNome?: string
  idade?: number
  destaqueAtivo?: boolean
  videoHabilitado?: boolean
  carrosselDisponivel?: boolean
  whatsappCardEnabled?: boolean
}

interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number?: number
  size?: number
}

function formatarNomeSlug(slug: string) {
  return slug
    .split("-")
    .filter(Boolean)
    .map((parte) => parte.charAt(0).toUpperCase() + parte.slice(1))
    .join(" ")
}

async function buscarAnunciosPorBairro(
  estado: string,
  cidade: string,
  bairro: string,
  page: number = 0
): Promise<PageResponse<AnuncioSeoDTO>> {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return { content: [], totalPages: 0, totalElements: 0 }

    const data = await serverApiFetchJson<any>(
      `${apiUrl}/anuncios/por-bairro/${encodeURIComponent(estado)}/${encodeURIComponent(cidade)}/${encodeURIComponent(bairro)}?page=${page}&size=20`,
      { next: { revalidate: 3600 } }
    )

    return {
      content: Array.isArray(data?.content) ? data.content : [],
      totalPages: Number(data?.totalPages ?? 0),
      totalElements: Number(data?.totalElements ?? 0),
      number: data?.number,
      size: data?.size,
    }
  } catch (error) {
    console.error("Erro ao buscar anúncios por bairro:", error)
    return { content: [], totalPages: 0, totalElements: 0 }
  }
}

async function buscarBairrosPorCidade(estado: string, cidade: string) {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return []

    const data = await serverApiFetchJson<any[]>(
      `${apiUrl}/anuncios/bairros-por-cidade/${encodeURIComponent(estado)}/${encodeURIComponent(cidade)}`,
      { next: { revalidate: 3600 } }
    )
    return data
  } catch (error) {
    console.error("Erro ao buscar bairros por cidade:", error)
    return []
  }
}

export async function generateMetadata({
  params,
  searchParams,
}: PageProps): Promise<Metadata> {
  const { estado, cidade, bairro } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: { index: false, follow: true },
    }
  }

  const data = await buscarAnunciosPorBairro(estado, cidade, bairro, page)

  if (!data.content || data.content.length === 0) {
    return {
      title: "Acompanhantes não encontradas",
      description: "Nenhuma acompanhante disponível nesta localidade.",
      robots: {
        index: false,
        follow: true,
      },
    }
  }

  const primeiroAnuncio = data.content[0]
  const bairroNome = primeiroAnuncio.bairroNome || formatarNomeSlug(bairro)
  const cidadeNome = primeiroAnuncio.cidadeNome || formatarNomeSlug(cidade)
  const estadoNome = primeiroAnuncio.estadoNome || estado.toUpperCase()
  const estadoUf = (primeiroAnuncio.estadoUf || estado).toUpperCase()

  const canonicalUrl = buildPublicUrl(
    buildPublicPath("acompanhantes", estado, cidade, bairro),
    page
  )

  const seo = gerarConteudoSeoBairro({
    bairroNome,
    cidadeNome,
    estadoNome,
    estadoUf,
  })

  const title = page > 0 ? `${seo.title} | Página ${page + 1}` : seo.title
  const description = page > 0 ? `${seo.metaDescription} Página ${page + 1}.` : seo.metaDescription

  const indexavel =
    page === 0 &&
    isBairroIndexavelLocal({
      totalAnunciosAtivos: data.totalElements || data.content.length,
    })

  return {
    title,
    description,
    alternates: {
      canonical: canonicalUrl,
    },
    openGraph: {
      title,
      description,
      url: canonicalUrl,
      type: "website",
      siteName: "Tops do Job",
      locale: "pt_BR",
    },
    robots: {
      index: indexavel,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  }
}

export default async function BairroPage({ params, searchParams }: PageProps) {
  const { estado, cidade, bairro } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) notFound()

  const data = await buscarAnunciosPorBairro(estado, cidade, bairro, page)
  const bairrosPorCidade = await buscarBairrosPorCidade(estado, cidade)

  if (!data.content || data.content.length === 0) {
    notFound()
  }

  const primeiroAnuncio = data.content[0]
  const bairroNome = primeiroAnuncio.bairroNome || formatarNomeSlug(bairro)
  const cidadeNome = primeiroAnuncio.cidadeNome || formatarNomeSlug(cidade)
  const estadoNome = primeiroAnuncio.estadoNome || estado.toUpperCase()
  const estadoUf = (primeiroAnuncio.estadoUf || estado).toUpperCase()
  const outrosBairros = bairrosPorCidade
    .filter((b: any) => b.bairroSlug !== bairro)
    .sort((a: any, b: any) => a.bairroNome.localeCompare(b.bairroNome))
    .slice(0, 10)

  const seo = gerarConteudoSeoBairro({
    bairroNome,
    cidadeNome,
    estadoNome,
    estadoUf,
    bairros: outrosBairros.map((bairroItem: any) => bairroItem.bairroNome).filter(Boolean),
  })

  const baseUrl = getPublicSiteBaseUrl()
  const estadoPath = buildPublicPath("acompanhantes", estado)
  const cidadePath = buildPublicPath("acompanhantes", estado, cidade)
  const bairroPath = buildPublicPath("acompanhantes", estado, cidade, bairro)
  const breadcrumbSchema = gerarBreadcrumbSchemaBairro(
    baseUrl,
    bairroNome,
    cidadeNome,
    estadoUf,
    cidade,
    bairro
  )

  const itemListSchema = {
    "@context": "https://schema.org",
    "@type": "ItemList",
    itemListElement: data.content.slice(0, 10).map((anuncio, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: anuncio.titulo,
      url: buildPublicUrl(buildPublicPath("anuncios", anuncio.slug)),
      image: selecionarCapaPublicaSegura(anuncio.midias)?.urlPublica || undefined,
    })),
  }

  const faqSchema = page === 0 ? gerarFaqSchema(seo.faq) : null
  const url = buildPublicUrl(bairroPath)
  const bairroLabel = labelAcompanhantesBairro(bairroNome)
  const bairroComparacaoTitulo = bairroLabel.replace("Acompanhantes", "Como comparar anúncios")
  const bairroSingularTitulo = `${bairroLabel.replace("Acompanhantes", "Acompanhante")}, ${cidadeNome}: como refinar sua busca`

  return (
    <main className="w-full mx-auto px-4 py-10 space-y-8">
      <nav className="public-breadcrumbs mb-6 text-sm text-gray-600">
        <Link href="/" className="hover:text-pink-600">
          Home
        </Link>
        <span className="mx-2">/</span>
        <Link href="/acompanhantes" className="hover:text-pink-600">
          Acompanhantes
        </Link>
        <span className="mx-2">/</span>
        <Link href={estadoPath} className="hover:text-pink-600">
          {estadoUf}
        </Link>
        <span className="mx-2">/</span>
        <Link href={cidadePath} className="hover:text-pink-600">
          {labelAcompanhantesCidade(cidadeNome)}
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">{labelAcompanhantesBairro(bairroNome)}</span>
      </nav>

      <div className="space-y-4">
        <h1 className="text-4xl font-bold text-gray-900">{seo.h1}</h1>
        <p className="text-lg text-gray-600">{seo.resumoTopo}</p>
      </div>

      <StoriesBar />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        {data.content.map((anuncio, index) => (
          <AnuncioCard
            key={`${anuncio.id}-${anuncio.slug ?? index}`}
            id={anuncio.id}
            slug={anuncio.slug}
            nome={anuncio.titulo}
            estadoUf={anuncio.estadoUf ?? null}
            cidadeNome={anuncio.cidadeNome ?? null}
            bairroNome={anuncio.bairroNome ?? null}
            idade={anuncio.idade}
            valor={`A partir de R$ ${Number(anuncio.preco ?? 0).toFixed(2)} / hora`}
            midias={anuncio.midias ?? []}
            descricao={anuncio.descricao}
            nomeAnunciante={anuncio.nomeAnunciante}
            usernameAnunciante={anuncio.usernameAnunciante}
            visualizacoes={anuncio.visualizacoes ?? 0}
            destaque={anuncio.destaqueAtivo ?? false}
            carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
            videoHabilitado={anuncio.videoHabilitado ?? false}
            whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
          />
        ))}
      </div>

      {page === 0 && (
        <section className="space-y-4 rounded-3xl border border-gray-200 bg-white p-6">
          <h2 className="text-2xl font-bold text-gray-900">
            Perfis disponíveis em {bairroNome}, {cidadeNome}
          </h2>
          <div className="space-y-3 text-gray-700">
            {seo.intro.map((paragrafo) => (
              <p key={paragrafo}>{paragrafo}</p>
            ))}
          </div>
        </section>
      )}

      {page === 0 && seo.comparacao.length > 0 && (
        <section className="space-y-4 border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">{bairroComparacaoTitulo}</h2>
          <div className="space-y-3 text-gray-700">
            {seo.comparacao.map((paragrafo) => (
              <p key={paragrafo}>{paragrafo}</p>
            ))}
          </div>
        </section>
      )}

      {page === 0 && (
        <section className="border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">{bairroSingularTitulo}</h2>
          <div className="mt-3 max-w-3xl space-y-3 text-sm leading-relaxed text-gray-600">
            {seo.singular.map((paragrafo) => (
              <p key={paragrafo}>{paragrafo}</p>
            ))}
          </div>
        </section>
      )}

      {data.totalPages > 1 && (
        <nav className="flex items-center justify-center gap-2 border-t py-8">
          {page > 0 && (
            <Link
              href={
                page === 1
                  ? bairroPath
                  : `${bairroPath}?page=${page - 1}`
              }
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              ← Anterior
            </Link>
          )}

          <div className="flex gap-1">
            {Array.from({ length: Math.min(data.totalPages, 5) }).map((_, i) => {
              const pageNum = i
              return (
                <Link
                  key={pageNum}
                  href={
                    pageNum === 0
                      ? bairroPath
                      : `${bairroPath}?page=${pageNum}`
                  }
                  className={`rounded-lg px-3 py-2 ${
                    page === pageNum
                      ? "bg-pink-600 text-white"
                      : "border border-gray-300 hover:bg-gray-100"
                  }`}
                >
                  {pageNum + 1}
                </Link>
              )
            })}
          </div>

          {page < data.totalPages - 1 && (
            <Link
              href={`${bairroPath}?page=${page + 1}`}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Próxima →
            </Link>
          )}
        </nav>
      )}

      {page === 0 && outrosBairros.length > 0 && (
        <section className="space-y-4 border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">
            Acompanhantes em outros bairros de {cidadeNome}
          </h2>
          <p className="max-w-3xl text-sm leading-relaxed text-gray-600">
            Use os links abaixo para continuar a navegação por regiões próximas de {cidadeNome},
            comparar anúncios ativos e voltar ao recorte de bairro que fizer mais sentido para a sua busca.
          </p>

          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
            {outrosBairros.map((bairroItem: any) => (
              <Link
                key={bairroItem.bairroSlug}
                href={buildPublicPath(
                  "acompanhantes",
                  estado,
                  cidade,
                  bairroItem.bairroSlug
                )}
                className="rounded-lg bg-pink-100 px-4 py-2 text-center text-sm font-medium text-pink-700 transition hover:bg-pink-200"
              >
                {labelAcompanhantesBairro(bairroItem.bairroNome)}
              </Link>
            ))}
          </div>
        </section>
      )}

      {page === 0 && seo.faq.length > 0 && (
        <section className="space-y-4 border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">Perguntas frequentes sobre a página</h2>
          <div className="space-y-3">
            {seo.faq.map((item) => (
              <div key={item.pergunta} className="rounded-2xl border border-gray-200 bg-white p-5">
                <h3 className="text-base font-semibold text-gray-900">{item.pergunta}</h3>
                <p className="mt-2 text-sm leading-6 text-gray-700">{item.resposta}</p>
              </div>
            ))}
          </div>
        </section>
      )}

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbSchema) }}
      />

      {faqSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(faqSchema) }}
        />
      )}

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(itemListSchema) }}
      />

      {page > 0 && (
        <link rel="prev" href={page === 1 ? url : `${url}?page=${page - 1}`} />
      )}

      {page < data.totalPages - 1 && (
        <link rel="next" href={`${url}?page=${page + 1}`} />
      )}
    </main>
  )
}
