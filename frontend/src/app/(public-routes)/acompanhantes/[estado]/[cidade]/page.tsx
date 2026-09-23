import { Metadata } from "next"
import Link from "next/link"
import { notFound } from "next/navigation"
import { cache } from "react"
import { ListagemPublicaPaginada } from "@/components/anuncios/listagem-publica-paginada"
import { StoriesBar } from "@/components/stories/stories-bar"
import {
  isPublicCatalogNotFound,
  listarPublicosPorCidade,
  obterAgregadoPublicoCidade,
} from "@/lib/public-catalog-server-api"
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
import { isBairroIndexavelLocal, isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"
import { gerarFaqSchema } from "@/lib/seo/programmatic-content"
import {
  buildPublicPageHref,
  buildPublicPath,
  buildPublicUrl,
  getPublicSiteBaseUrl,
  isCleanPublicFirstPage,
  isPublicPageOutOfRange,
  parsePublicOrderSeed,
  parsePublicPage,
} from "@/lib/seo/public-url"


interface PageProps {
  params: Promise<{
    estado: string
    cidade: string
  }>
  searchParams: Promise<Record<string, string | string[] | undefined>>
}

const carregarCidade = cache(async (estado: string, cidade: string, page: number, ordemSeed?: string) => {
  const [data, agregadoBase] = await Promise.all([
    listarPublicosPorCidade(estado, cidade, page, 20, ordemSeed),
    obterAgregadoPublicoCidade(estado, cidade),
  ])
  const agregado: CidadeSeoAggregate = {
    ...agregadoBase,
    totalBairrosAtivos: agregadoBase.bairros.length,
    totalCategoriasAtivas: agregadoBase.categoriasPrincipais.length,
  }
  return { data, agregado }
})

export async function generateMetadata({ params, searchParams }: PageProps): Promise<Metadata> {
  const { estado, cidade } = await params
  const query = await searchParams
  const pageValue = query.page
  const page = parsePublicPage(pageValue)
  const ordemSeed = parsePublicOrderSeed(query.ordemSeed)
  if (page === null || ordemSeed === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: buildPublicRobotsMetadata(false),
    }
  }

  try {
    const { data, agregado } = await carregarCidade(estado, cidade, page, ordemSeed)
    if (isPublicPageOutOfRange(page, data.paginacao)) {
      return {
        title: "Página inválida | Tops do Job",
        robots: buildPublicRobotsMetadata(false),
      }
    }
    const canonicalUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado, cidade), page)
    const indexavel =
      isCleanPublicFirstPage(pageValue, page) && isCidadeIndexavelLocal(agregado)
    return {
      title: gerarTituloMetadataCidade(agregado, page),
      description: gerarDescricaoMetadataCidade(agregado, page),
      alternates: { canonical: canonicalUrl },
      openGraph: {
        title: gerarTituloMetadataCidade(agregado, page),
        description: gerarDescricaoMetadataCidade(agregado, page),
        url: canonicalUrl,
        type: "website",
        siteName: "Tops do Job",
        locale: "pt_BR",
      },
      robots: buildPublicRobotsMetadata(indexavel, true),
    }
  } catch (error) {
    if (isPublicCatalogNotFound(error)) {
      return {
        title: "Perfis não encontrados",
        description: "Nenhum perfil ativo foi encontrado nesta localidade.",
        robots: buildPublicRobotsMetadata(false),
      }
    }
    throw error
  }
}

export default async function CidadePage({ params, searchParams }: PageProps) {
  const { estado, cidade } = await params
  const query = await searchParams
  const page = parsePublicPage(query.page)
  const ordemSeed = parsePublicOrderSeed(query.ordemSeed)
  if (page === null || ordemSeed === null) notFound()
  const cidadePath = buildPublicPath("acompanhantes", estado, cidade)

  let carregado
  try {
    carregado = await carregarCidade(estado, cidade, page, ordemSeed)
  } catch (error) {
    if (isPublicCatalogNotFound(error)) notFound()
    throw error
  }
  const { data, agregado } = carregado
  if (isPublicPageOutOfRange(page, data.paginacao)) notFound()
  const editorial = await gerarConteudoProgramaticoCidade(agregado)
  const baseUrl = getPublicSiteBaseUrl()
  const estadoPath = buildPublicPath("acompanhantes", estado)
  const cidadeLabel = labelAcompanhantesCidade(agregado.cidadeNome)
  const h1 = cidadeLabel
  const descricaoTopoSeo = gerarDescricaoTopoCidade(agregado)
  const breadcrumbSchema = gerarBreadcrumbSchemaCidade(baseUrl, agregado)
  const faqSchema = page === 0 ? gerarFaqSchema(editorial.faq) : null
  const itemListSchema =
    page === 0 ? gerarItemListSchemaCidade(baseUrl, data.itens.slice(0, 10)) : null
  const bairrosVisiveis = [...agregado.bairros]
    .sort(
      (a, b) =>
        Number(isBairroIndexavelLocal(b)) - Number(isBairroIndexavelLocal(a)) ||
        a.bairroNome.localeCompare(b.bairroNome)
    )
    .slice(0, editorial.modo === "completo" ? 10 : 6)
  const categoriasVisiveis = agregado.categoriasPrincipais.slice(0, editorial.modo === "completo" ? 5 : 3)
  const cidadesRelacionadas = [...agregado.cidadesRelacionadas]
    .sort(
      (a, b) =>
        Number(isCidadeIndexavelLocal(b)) - Number(isCidadeIndexavelLocal(a)) ||
        a.cidadeNome.localeCompare(b.cidadeNome)
    )
    .slice(0, editorial.modo === "completo" ? 8 : 4)
  return (
    <main className="mx-auto w-full space-y-8 px-4 py-10">
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
          {agregado.estadoUf}
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">{cidadeLabel}</span>
      </nav>

      <div className="space-y-4">
        <h1 className="break-normal text-3xl font-bold text-gray-900 sm:text-4xl">{h1}</h1>
        <div className="max-w-4xl rounded-2xl border border-pink-100 bg-pink-50/60 px-5 py-4">
          <p className="text-base leading-7 text-gray-700">{descricaoTopoSeo}</p>
        </div>
      </div>

      <StoriesBar />

      <ListagemPublicaPaginada
        caminhoBase={cidadePath}
        initialData={data}
        searchParams={query}
      />

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
        dangerouslySetInnerHTML={{ __html: serializeJsonLd(breadcrumbSchema) }}
      />

      {itemListSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: serializeJsonLd(itemListSchema) }}
        />
      )}

      {faqSchema && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: serializeJsonLd(faqSchema) }}
        />
      )}

      {page > 0 && <link rel="prev" href={new URL(buildPublicPageHref(cidadePath, page - 1, data.paginacao.ordemSeed, query), baseUrl).toString()} />}
      {page < data.paginacao.totalPaginas - 1 && <link rel="next" href={new URL(buildPublicPageHref(cidadePath, page + 1, data.paginacao.ordemSeed, query), baseUrl).toString()} />}
    </main>
  )
}
