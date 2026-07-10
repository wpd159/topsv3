import { Metadata } from "next"
import Link from "next/link"
import { notFound } from "next/navigation"
import AnuncioCard from "@/components/anuncios/anuncio-card"
import { StoriesBar } from "@/components/stories/stories-bar"
import { serverApiFetchJson } from "@/lib/server-api"
import {
  CidadeSeoAggregate,
  gerarBreadcrumbSchemaCidade,
  gerarConteudoProgramaticoCidade,
  gerarDescricaoMetadataCidade,
  gerarDescricaoTopoCidade,
  gerarItemListSchemaCidade,
  gerarTituloMetadataCidade,
} from "@/lib/seo/cidadeSeo"
import {
  labelAcompanhantesBairro,
  labelAcompanhantesCidade,
} from "@/lib/seo/local-labels"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
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
  fotosUrl?: string[]
  videosAnuncio?: string[]
  descricao?: string
  telefoneAnunciante?: string
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
  contentClassification?: string
  requiresVisitorVerification?: boolean
  requiresStrongVerification?: boolean
  viewerAuthorized?: boolean
  restrictedPreview?: boolean
  whatsappCardEnabled?: boolean
}

interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number?: number
  size?: number
}

function formatarNomeCidade(slug: string) {
  return slug
    .split("-")
    .filter(Boolean)
    .map((parte) => parte.charAt(0).toUpperCase() + parte.slice(1))
    .join(" ")
}

function buildCityHref(cidadePath: string, page: number) {
  return page <= 0 ? cidadePath : `${cidadePath}?page=${page}`
}

async function buscarAnunciosPorCidade(
  estado: string,
  cidade: string,
  page: number = 0
): Promise<PageResponse<AnuncioSeoDTO>> {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return { content: [], totalPages: 0, totalElements: 0 }

    const data = await serverApiFetchJson<any>(
      `${apiUrl}/anuncios/por-cidade/${encodeURIComponent(estado)}/${encodeURIComponent(cidade)}?page=${page}&size=20`,
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
    console.error("Erro ao buscar anúncios por cidade:", error)
    return { content: [], totalPages: 0, totalElements: 0 }
  }
}

async function buscarAgregadoCidade(
  estado: string,
  cidade: string
): Promise<CidadeSeoAggregate | null> {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return null

    const data = await serverApiFetchJson<CidadeSeoAggregate>(
      `${apiUrl}/anuncios/seo/cidade/${encodeURIComponent(estado)}/${encodeURIComponent(cidade)}`,
      { next: { revalidate: 3600 } }
    )
    return data
  } catch (error) {
    console.error("Erro ao buscar agregado SEO da cidade:", error)
    return null
  }
}

function criarFallbackAgregado(
  estado: string,
  cidade: string,
  data: PageResponse<AnuncioSeoDTO>
): CidadeSeoAggregate {
  const primeiroAnuncio = data.content[0]

  return {
    estadoUf: (primeiroAnuncio?.estadoUf || estado).toUpperCase(),
    estadoNome: primeiroAnuncio?.estadoNome || (primeiroAnuncio?.estadoUf || estado).toUpperCase(),
    cidadeNome: primeiroAnuncio?.cidadeNome || formatarNomeCidade(cidade),
    cidadeSlug: cidade,
    totalAnunciosAtivos: data.totalElements || data.content.length,
    totalBairrosAtivos: 0,
    totalCategoriasAtivas: 1,
    quantidadeAnunciosDestaque: 0,
    quantidadeAnunciosRecentes: 0,
    ultimaAtualizacao: null,
    shouldIndex: false,
    robots: "noindex,follow",
    reasonCodes: ["FALLBACK_SEM_AGREGADOR"],
    bairros: [],
    categoriasPrincipais: [],
    cidadesRelacionadas: [],
    possuiStories: false,
    possuiConteudoRestrito: data.content.some((item) => item.requiresVisitorVerification),
    possuiDestaques: false,
    possuiRecentes: false,
  }
}

export async function generateMetadata({ params, searchParams }: PageProps): Promise<Metadata> {
  const { estado, cidade } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: { index: false, follow: true },
    }
  }

  const [data, agregadoBruto] = await Promise.all([
    buscarAnunciosPorCidade(estado, cidade, page),
    buscarAgregadoCidade(estado, cidade),
  ])

  if ((!data.content || data.content.length === 0) && !agregadoBruto) {
    return {
      title: "Perfis não encontrados",
      description: "Nenhum perfil ativo foi encontrado nesta localidade.",
      robots: {
        index: false,
        follow: true,
      },
    }
  }

  const agregado = agregadoBruto ?? criarFallbackAgregado(estado, cidade, data)
  const canonicalUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado, cidade), page)
  const indexavel = page === 0 && isCidadeIndexavelLocal(agregado)

  return {
    title: gerarTituloMetadataCidade(agregado, page),
    description: gerarDescricaoMetadataCidade(agregado, page),
    alternates: {
      canonical: canonicalUrl,
    },
    openGraph: {
      title: gerarTituloMetadataCidade(agregado, page),
      description: gerarDescricaoMetadataCidade(agregado, page),
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

export default async function CidadePage({ params, searchParams }: PageProps) {
  const { estado, cidade } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) notFound()

  const [data, agregadoBruto] = await Promise.all([
    buscarAnunciosPorCidade(estado, cidade, page),
    buscarAgregadoCidade(estado, cidade),
  ])

  if (!data.content || data.content.length === 0) {
    notFound()
  }

  const agregado = agregadoBruto ?? criarFallbackAgregado(estado, cidade, data)
  const editorial = await gerarConteudoProgramaticoCidade(agregado)
  const baseUrl = getPublicSiteBaseUrl()
  const estadoPath = buildPublicPath("acompanhantes", estado)
  const cidadePath = buildPublicPath("acompanhantes", estado, cidade)
  const cidadeLabel = labelAcompanhantesCidade(agregado.cidadeNome)
  const h1 = cidadeLabel
  const descricaoTopoSeo = gerarDescricaoTopoCidade(agregado)
  const breadcrumbSchema = gerarBreadcrumbSchemaCidade(baseUrl, agregado)
  const faqSchema = page === 0 ? gerarFaqSchema(editorial.faq) : null
  const itemListSchema =
    page === 0 ? gerarItemListSchemaCidade(baseUrl, data.content.slice(0, 10)) : null
  const bairrosVisiveis = agregado.bairros.slice(0, editorial.modo === "completo" ? 10 : 6)
  const categoriasVisiveis = agregado.categoriasPrincipais.slice(0, editorial.modo === "completo" ? 5 : 3)
  const cidadesRelacionadas = agregado.cidadesRelacionadas
    .filter(isCidadeIndexavelLocal)
    .slice(0, editorial.modo === "completo" ? 8 : 4)
  const url = buildPublicUrl(cidadePath)

  return (
    <main className="mx-auto w-full space-y-8 px-4 py-10">
      <nav className="mb-6 text-sm text-gray-600">
        <Link href="/" className="hover:text-pink-600">
          Home
        </Link>
        <span className="mx-2">/</span>
        <Link href="/acompanhantes" className="hover:text-pink-600">
          Acompanhantes
        </Link>
        <span className="mx-2">/</span>
        <Link href={estadoPath} className="hover:text-pink-600">
          {agregado.estadoUf}
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">{cidadeLabel}</span>
      </nav>

      <div className="space-y-4">
        <h1 className="text-4xl font-bold text-gray-900">{h1}</h1>
        <div className="max-w-4xl rounded-2xl border border-pink-100 bg-pink-50/60 px-5 py-4">
          <p className="text-base leading-7 text-gray-700">{descricaoTopoSeo}</p>
        </div>
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
            imagens={Array.isArray(anuncio.fotosUrl) ? anuncio.fotosUrl : []}
            videos={Array.isArray(anuncio.videosAnuncio) ? anuncio.videosAnuncio : []}
            descricao={anuncio.descricao}
            telefone={anuncio.telefoneAnunciante}
            nomeAnunciante={anuncio.nomeAnunciante}
            usernameAnunciante={anuncio.usernameAnunciante}
            visualizacoes={anuncio.visualizacoes ?? 0}
            destaque={anuncio.destaqueAtivo ?? false}
            carrosselDisponivel={anuncio.carrosselDisponivel ?? false}
            videoHabilitado={anuncio.videoHabilitado ?? false}
            contentClassification={anuncio.contentClassification ?? null}
            requiresVisitorVerification={anuncio.requiresVisitorVerification ?? false}
            requiresStrongVerification={anuncio.requiresStrongVerification ?? false}
            viewerAuthorized={anuncio.viewerAuthorized ?? false}
            restrictedPreview={anuncio.restrictedPreview ?? false}
            whatsappCardEnabled={anuncio.whatsappCardEnabled ?? false}
          />
        ))}
      </div>

      {data.totalPages > 1 && (
        <nav className="flex items-center justify-center gap-2 border-t py-8">
          {page > 0 && (
            <Link
              href={buildCityHref(cidadePath, page - 1)}
              scroll={false}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Anterior
            </Link>
          )}

          <div className="flex gap-1">
            {Array.from({ length: Math.min(data.totalPages, 5) }).map((_, index) => {
              const pageNum = index
              return (
                <Link
                  key={pageNum}
                  href={buildCityHref(cidadePath, pageNum)}
                  scroll={false}
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
              href={buildCityHref(cidadePath, page + 1)}
              scroll={false}
              className="rounded-lg border border-gray-300 px-4 py-2 hover:bg-gray-100"
            >
              Próxima
            </Link>
          )}
        </nav>
      )}

      {page === 0 && (
        <>
          <section className="space-y-4 rounded-3xl border border-gray-200 bg-white p-6">
            <h2 className="text-2xl font-bold text-gray-900">
              Panorama de acompanhantes em {agregado.cidadeNome}
            </h2>
            <div className="space-y-3 text-gray-700">
              {editorial.intro.map((paragrafo) => (
                <p key={paragrafo}>{paragrafo}</p>
              ))}
            </div>
          </section>

          {(bairrosVisiveis.length > 0 || editorial.bairros.length > 0) && (
            <section className="space-y-4 border-t pt-8">
              <h2 className="text-2xl font-bold text-gray-900">
                Acompanhantes por bairro em {agregado.cidadeNome}
              </h2>
              <div className="space-y-3 text-gray-700">
                {editorial.bairros.map((paragrafo) => (
                  <p key={paragrafo}>{paragrafo}</p>
                ))}
              </div>

              {bairrosVisiveis.length > 0 && (
                <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
                  {bairrosVisiveis.map((bairroItem) => (
                    <Link
                      key={bairroItem.bairroSlug}
                      href={buildPublicPath(
                        "acompanhantes",
                        estado,
                        cidade,
                        bairroItem.bairroSlug
                      )}
                      className="rounded-lg bg-blue-100 px-4 py-3 text-center text-sm font-medium text-blue-700 transition hover:bg-blue-200"
                    >
                      {labelAcompanhantesBairro(bairroItem.bairroNome)}
                    </Link>
                  ))}
                </div>
              )}
            </section>
          )}

          {editorial.comoUsar.length > 0 && (
            <section className="space-y-4 border-t pt-8">
              <h2 className="text-2xl font-bold text-gray-900">
                Como usar esta página para encontrar acompanhantes em {agregado.cidadeNome}
              </h2>
              <div className="space-y-3 text-gray-700">
                {editorial.comoUsar.map((paragrafo) => (
                  <p key={paragrafo}>{paragrafo}</p>
                ))}
              </div>
            </section>
          )}

          {(categoriasVisiveis.length > 0 || editorial.navegacao.length > 0) && (
            <section className="space-y-4 border-t pt-8">
              <h2 className="text-2xl font-bold text-gray-900">
                Acompanhantes em {agregado.cidadeNome} por perfil e região
              </h2>
              <div className="space-y-3 text-gray-700">
                {editorial.navegacao.map((paragrafo) => (
                  <p key={paragrafo}>{paragrafo}</p>
                ))}
              </div>

              {categoriasVisiveis.length > 0 && (
                <div className="flex flex-wrap gap-3">
                  {categoriasVisiveis.map((categoria) => (
                    <span
                      key={categoria.codigo}
                      className="rounded-full border border-pink-200 bg-pink-50 px-4 py-2 text-sm font-medium text-pink-700"
                    >
                      {categoria.nome}
                    </span>
                  ))}
                </div>
              )}
            </section>
          )}
        </>
      )}

      {cidadesRelacionadas.length > 0 && page === 0 && (
        <section className="space-y-4 border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">
            Outras cidades próximas em {agregado.estadoUf}
          </h2>
          <p className="max-w-3xl text-sm leading-relaxed text-gray-600">
            Amplie a navegação para outras cidades do mesmo estado e continue explorando páginas
            locais conectadas, com anúncios ativos e contexto regional semelhante.
          </p>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
            {cidadesRelacionadas.map((cidadeItem) => (
              <Link
                key={cidadeItem.cidadeSlug}
                href={buildPublicPath("acompanhantes", estado, cidadeItem.cidadeSlug)}
                className="rounded-lg bg-pink-100 px-4 py-3 text-center text-sm font-medium text-pink-700 transition hover:bg-pink-200"
              >
                {labelAcompanhantesCidade(cidadeItem.cidadeNome)}
              </Link>
            ))}
          </div>
        </section>
      )}

      {page === 0 && (
        <section className="border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">
            Acompanhante em {agregado.cidadeNome}: como refinar sua busca
          </h2>
          <div className="mt-3 max-w-3xl space-y-3 text-sm leading-relaxed text-gray-600">
            {editorial.singular.map((paragrafo) => (
              <p key={paragrafo}>{paragrafo}</p>
            ))}
          </div>
        </section>
      )}

      {page === 0 && editorial.faq.length > 0 && (
        <section className="space-y-4 border-t pt-8">
          <h2 className="text-2xl font-bold text-gray-900">Perguntas frequentes sobre a página</h2>
          <div className="space-y-3">
            {editorial.faq.map((item) => (
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

      {itemListSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(itemListSchema) }}
        />
      )}

      {faqSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(faqSchema) }}
        />
      )}

      {page > 0 && <link rel="prev" href={page === 1 ? url : `${url}?page=${page - 1}`} />}
      {page < data.totalPages - 1 && <link rel="next" href={`${url}?page=${page + 1}`} />}
    </main>
  )
}
