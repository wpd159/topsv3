import { Metadata } from "next"
import { notFound } from "next/navigation"
import Link from "next/link"
import AnuncioCard from "@/components/anuncios/anuncio-card"
import { StoriesBar } from "@/components/stories/stories-bar"
import { gerarDescricaoSeoEstado, gerarTituloSeoEstado } from "@/lib/seo/public-metadata"
import { serverApiFetchJson } from "@/lib/server-api"
import { selecionarCapaPublicaSegura, type MidiaPublica } from "@/lib/media/public-media"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import { getEstadoNomePorUf } from "@/lib/seo/acompanhantes-navigation"
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

interface CidadeAtivaDTO {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  ultimaAtualizacao?: string
  shouldIndex?: boolean
  totalAnunciosAtivos?: number
  totalAnuncios?: number
  quantidadeAnuncios?: number
}

interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number?: number
  size?: number
}

async function buscarAnunciosPorEstado(
  estado: string,
  page: number = 0
): Promise<PageResponse<AnuncioSeoDTO>> {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return { content: [], totalPages: 0, totalElements: 0 }

    const data = await serverApiFetchJson<any>(
      `${apiUrl}/anuncios/por-estado/${encodeURIComponent(estado)}?page=${page}&size=20`,
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
    console.error("Erro ao buscar anúncios por estado:", error)
    return { content: [], totalPages: 0, totalElements: 0 }
  }
}

async function buscarCidadesPorEstado(estado: string): Promise<CidadeAtivaDTO[]> {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return []

    const data = await serverApiFetchJson<any[]>(
      `${apiUrl}/anuncios/cidades-por-estado/${encodeURIComponent(estado)}`,
      { next: { revalidate: 3600 } }
    )
    return Array.isArray(data) ? data : []
  } catch (error) {
    console.error("Erro ao buscar cidades por estado:", error)
    return []
  }
}

function gerarBreadcrumbSchemaEstado(baseUrl: string, estadoUf: string) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      {
        "@type": "ListItem",
        position: 1,
        name: "Home",
        item: baseUrl,
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "Acompanhantes",
        item: `${baseUrl}/acompanhantes`,
      },
      {
        "@type": "ListItem",
        position: 3,
        name: estadoUf,
        item: `${baseUrl}/acompanhantes/${estadoUf.toLowerCase()}`,
      },
    ],
  }
}

function gerarConteudoSeoEstado(estadoNome: string, estadoUf: string) {
  return `
    <h2>Acompanhantes em ${estadoNome} – ${estadoUf}</h2>

    <p>
      Encontre acompanhantes em ${estadoNome} com anúncios atualizados, fotos nos perfis e contato direto.
      Nossa plataforma reúne perfis ativos em diferentes cidades do estado para facilitar a busca por acompanhantes,
      escorts e perfis premium com mais praticidade.
    </p>

    <h2>Cidades com anúncios ativos em ${estadoNome}</h2>

    <p>
      A página de ${estadoNome} centraliza anúncios publicados em várias cidades do estado.
      Isso ajuda o visitante a explorar regiões próximas, comparar perfis e navegar com mais facilidade
      entre os principais polos urbanos onde há anúncios disponíveis.
    </p>

    <h2>Busca regional com mais praticidade</h2>

    <p>
      Ao acessar a página de ${estadoNome}, você consegue visualizar acompanhantes disponíveis em diversas cidades,
      expandindo a pesquisa para além de um único município. Essa navegação regional torna a experiência mais rápida,
      organizada e útil para quem quer comparar opções dentro do mesmo estado.
    </p>

    <h2>Perfis atualizados e contato direto</h2>

    <p>
      Os anúncios ativos exibem informações objetivas sobre descrição, localização, faixa de valor, imagens e formas de contato.
      Assim, fica mais fácil encontrar perfis compatíveis com o que você procura e seguir para a cidade ou anúncio que fizer mais sentido.
    </p>

    <h2>Navegue por cidade em ${estadoNome}</h2>

    <p>
      Além da listagem geral do estado, você também pode acessar páginas específicas por cidade para refinar a navegação.
      Isso permite explorar bairros, visualizar detalhes locais e encontrar acompanhantes em regiões mais próximas dentro de ${estadoNome}.
    </p>
  `
}

export async function generateMetadata({
  params,
  searchParams,
}: PageProps): Promise<Metadata> {
  const { estado } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: { index: false, follow: true },
    }
  }
  const [data, cidadesPorEstado] = await Promise.all([
    buscarAnunciosPorEstado(estado, page),
    buscarCidadesPorEstado(estado),
  ])

  const estadoUf = estado.toUpperCase()
  const estadoNome =
    data.content?.[0]?.estadoNome || getEstadoNomePorUf(estadoUf)

  const totalCidades = cidadesPorEstado.length

  const canonicalUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado), page)

  const title = gerarTituloSeoEstado({
    estadoNome,
    estadoUf,
    page,
  })
  const description = gerarDescricaoSeoEstado({
    estadoNome,
    totalAnuncios: data.totalElements || data.content.length,
    totalCidades,
    page,
  })
  const temCidadeIndexavel = cidadesPorEstado.some(isCidadeIndexavelLocal)

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
      index: page === 0 && temCidadeIndexavel,
      follow: true,
      "max-image-preview": "large",
      "max-snippet": -1,
      "max-video-preview": -1,
    },
  }
}

export default async function EstadoPage({ params, searchParams }: PageProps) {
  const { estado } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) notFound()

  const data = await buscarAnunciosPorEstado(estado, page)
  const cidadesPorEstado = await buscarCidadesPorEstado(estado)

  if (!data.content || data.content.length === 0) {
    notFound()
  }

  const estadoUf = (data.content[0]?.estadoUf || estado).toUpperCase()
  const estadoNome =
    data.content[0]?.estadoNome || getEstadoNomePorUf(estadoUf)

  const h1 = `Acompanhantes em ${estadoNome} – ${estadoUf}`
  const descricaoTopo = `Encontre acompanhantes em ${estadoNome}. Veja perfis ativos por cidade, com fotos nos anúncios, contato direto e navegação local.`
  const seoContent = gerarConteudoSeoEstado(estadoNome, estadoUf)

  const baseUrl = getPublicSiteBaseUrl()
  const estadoPath = buildPublicPath("acompanhantes", estado)
  const breadcrumbSchema = gerarBreadcrumbSchemaEstado(baseUrl, estadoUf)

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

  const cidadesOrdenadas = [...cidadesPorEstado]
    .filter(isCidadeIndexavelLocal)
    .sort((a, b) => a.cidadeNome.localeCompare(b.cidadeNome))
    .slice(0, 15)

  const url = buildPublicUrl(estadoPath)

  return (
    <main className="w-full mx-auto px-4 py-10 space-y-8">
      <nav className="text-sm text-gray-600 mb-6">
        <Link href="/" className="hover:text-pink-600">
          Home
        </Link>
        <span className="mx-2">/</span>
        <Link href="/acompanhantes" className="hover:text-pink-600">
          Acompanhantes
        </Link>
        <span className="mx-2">/</span>
        <span className="text-gray-900 font-medium">{estadoUf}</span>
      </nav>

      <div className="space-y-4">
        <h1 className="text-4xl font-bold text-gray-900">{h1}</h1>
        <p className="text-lg text-gray-600">{descricaoTopo}</p>
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
        <section className="prose prose-sm max-w-none text-gray-700 space-y-4">
          <div dangerouslySetInnerHTML={{ __html: seoContent }} />
        </section>
      )}

      {data.totalPages > 1 && (
        <nav className="flex justify-center items-center gap-2 py-8 border-t">
          {page > 0 && (
            <Link
              href={page === 1 ? estadoPath : `${estadoPath}?page=${page - 1}`}
              className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-100"
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
                  href={pageNum === 0 ? estadoPath : `${estadoPath}?page=${pageNum}`}
                  className={`px-3 py-2 rounded-lg ${
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
              href={`${estadoPath}?page=${page + 1}`}
              className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-100"
            >
              Próxima →
            </Link>
          )}
        </nav>
      )}

      {cidadesOrdenadas.length > 0 && page === 0 && (
        <section className="border-t pt-8 space-y-4">
          <h2 className="text-2xl font-bold text-gray-900">
            Acompanhantes em cidades de {estadoUf}
          </h2>
          <p className="max-w-3xl text-sm leading-relaxed text-gray-600">
            Explore as principais cidades deste estado para continuar a navegação por
            páginas locais com anúncios ativos, contexto regional e links internos mais
            específicos.
          </p>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
            {cidadesOrdenadas.map((cidadeItem) => (
              <Link
                key={cidadeItem.cidadeSlug}
                href={buildPublicPath("acompanhantes", estado, cidadeItem.cidadeSlug)}
                className="px-4 py-2 bg-pink-100 text-pink-700 rounded-lg hover:bg-pink-200 transition text-center text-sm font-medium"
              >
                {labelAcompanhantesCidade(cidadeItem.cidadeNome)}
              </Link>
            ))}
          </div>
        </section>
      )}

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbSchema) }}
      />

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
