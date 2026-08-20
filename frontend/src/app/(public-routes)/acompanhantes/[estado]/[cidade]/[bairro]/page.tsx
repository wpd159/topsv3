import { Metadata } from "next"
import { notFound } from "next/navigation"
import Link from "next/link"
import { cache } from "react"
import {
  gerarConteudoSeoBairro,
  gerarBreadcrumbSchemaBairro,
} from "@/lib/seo/seoContentGeneratorBairro"
import { ListagemPublicaPaginada } from "@/components/anuncios/listagem-publica-paginada"
import { StoriesBar } from "@/components/stories/stories-bar"
import { fontePublicaSegura, selecionarCapaPublicaSegura } from "@/lib/media/public-media"
import {
  isPublicCatalogNotFound,
  listarPublicosPorBairro,
  obterAgregadoPublicoCidade,
} from "@/lib/public-catalog-server-api"
import {
  labelAcompanhantesBairro,
  labelAcompanhantesCidade,
} from "@/lib/seo/local-labels"
import { isBairroIndexavelLocal } from "@/lib/seo/local-indexing"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"
import { gerarFaqSchema } from "@/lib/seo/programmatic-content"
import {
  buildPublicPath,
  buildPublicUrl,
  getPublicSiteBaseUrl,
  isCleanPublicFirstPage,
  isPublicPageOutOfRange,
  parsePublicPage,
} from "@/lib/seo/public-url"

export const dynamic = "force-dynamic"

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

const carregarBairro = cache(async (estado: string, cidade: string, bairro: string, page: number) => {
  const [data, agregadoCidade] = await Promise.all([
    listarPublicosPorBairro(estado, cidade, bairro, page),
    obterAgregadoPublicoCidade(estado, cidade),
  ])
  return { data, agregadoCidade }
})

export async function generateMetadata({
  params,
  searchParams,
}: PageProps): Promise<Metadata> {
  const { estado, cidade, bairro } = await params
  const pageValue = (await searchParams).page
  const page = parsePublicPage(pageValue)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: buildPublicRobotsMetadata(false),
    }
  }

  let carregado
  try {
    carregado = await carregarBairro(estado, cidade, bairro, page)
  } catch (error) {
    if (isPublicCatalogNotFound(error)) {
      return {
        title: "Acompanhantes não encontradas",
        description: "Nenhuma acompanhante disponível nesta localidade.",
        robots: buildPublicRobotsMetadata(false),
      }
    }
    throw error
  }
  const { data, agregadoCidade } = carregado
  if (isPublicPageOutOfRange(page, data.paginacao)) {
    return {
      title: "Página inválida | Tops do Job",
      robots: buildPublicRobotsMetadata(false),
    }
  }
  const bairroAgregado = agregadoCidade.bairros.find(
    (item) => item.bairroSlug.toLowerCase() === bairro.toLowerCase()
  )

  const bairroNome = data.localidade.bairro as string
  const cidadeNome = data.localidade.cidade as string
  const estadoNome = data.localidade.estado
  const estadoUf = data.localidade.uf

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
    isCleanPublicFirstPage(pageValue, page) && isBairroIndexavelLocal(bairroAgregado)

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
    robots: buildPublicRobotsMetadata(indexavel, true),
  }
}

export default async function BairroPage({ params, searchParams }: PageProps) {
  const { estado, cidade, bairro } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) notFound()

  let carregado
  try {
    carregado = await carregarBairro(estado, cidade, bairro, page)
  } catch (error) {
    if (isPublicCatalogNotFound(error)) notFound()
    throw error
  }
  const { data, agregadoCidade } = carregado
  if (isPublicPageOutOfRange(page, data.paginacao)) notFound()
  const bairroAgregado = agregadoCidade.bairros.find(
    (item) => item.bairroSlug.toLowerCase() === bairro.toLowerCase()
  )
  if (!bairroAgregado) notFound()
  const bairroNome = data.localidade.bairro as string
  const cidadeNome = data.localidade.cidade as string
  const estadoNome = data.localidade.estado
  const estadoUf = data.localidade.uf
  const outrosBairros = agregadoCidade.bairros
    .filter((item) => item.bairroSlug !== bairro)
    .sort(
      (a, b) =>
        Number(isBairroIndexavelLocal(b)) - Number(isBairroIndexavelLocal(a)) ||
        a.bairroNome.localeCompare(b.bairroNome)
    )
    .slice(0, 10)

  const seo = gerarConteudoSeoBairro({
    bairroNome,
    cidadeNome,
    estadoNome,
    estadoUf,
    bairros: outrosBairros.map((bairroItem) => bairroItem.bairroNome).filter(Boolean),
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
    itemListElement: data.itens.slice(0, 10).map((anuncio, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: anuncio.titulo,
      url: buildPublicUrl(buildPublicPath("anuncios", anuncio.slug)),
      image: (() => {
        const capa = selecionarCapaPublicaSegura(anuncio.midias)
        return capa ? fontePublicaSegura(capa) ?? undefined : undefined
      })(),
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
        <h1 className="break-normal text-3xl font-bold text-gray-900 sm:text-4xl">{seo.h1}</h1>
        <p className="text-lg text-gray-600">{seo.resumoTopo}</p>
      </div>

      <StoriesBar />

      <ListagemPublicaPaginada
        key={data.paginacao.ordemSeed}
        caminhoBase={bairroPath}
        escopo={{ tipo: "bairro", uf: estado, cidade, bairro }}
        initialData={data}
      >

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
      </ListagemPublicaPaginada>

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
            {outrosBairros.map((bairroItem) => (
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
        dangerouslySetInnerHTML={{ __html: serializeJsonLd(breadcrumbSchema) }}
      />

      {faqSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: serializeJsonLd(faqSchema) }}
        />
      )}

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: serializeJsonLd(itemListSchema) }}
      />

      {page > 0 && (
        <link rel="prev" href={page === 1 ? url : `${url}?page=${page - 1}`} />
      )}

      {page < data.paginacao.totalPaginas - 1 && (
        <link rel="next" href={`${url}?page=${page + 1}`} />
      )}
    </main>
  )
}
