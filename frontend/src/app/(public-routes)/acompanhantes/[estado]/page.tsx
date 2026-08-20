import { Metadata } from "next"
import { notFound } from "next/navigation"
import Link from "next/link"
import { cache } from "react"
import { ListagemPublicaPaginada } from "@/components/anuncios/listagem-publica-paginada"
import { StoriesBar } from "@/components/stories/stories-bar"
import { gerarDescricaoSeoEstado, gerarTituloSeoEstado } from "@/lib/seo/public-metadata"
import { fontePublicaSegura, selecionarCapaPublicaSegura } from "@/lib/media/public-media"
import {
  descobrirLocalidadesPublicas,
  isPublicCatalogNotFound,
  listarPublicosPorEstado,
} from "@/lib/public-catalog-server-api"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"
import { getEstadoNomePorUf } from "@/lib/seo/acompanhantes-navigation"
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
  }>
  searchParams: Promise<{
    page?: string
  }>
}

const carregarEstado = cache(async (estado: string, page: number) => {
  const [data, descoberta] = await Promise.all([
    listarPublicosPorEstado(estado, page),
    descobrirLocalidadesPublicas(),
  ])
  const estadoDescoberto = descoberta.estados.find(
    (item) => item.uf.toLowerCase() === estado.toLowerCase()
  )
  if (!estadoDescoberto) {
    throw new Error(`Estado ${estado} ausente da descoberta publica.`)
  }
  return { data, estadoDescoberto }
})

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
  const pageValue = (await searchParams).page
  const page = parsePublicPage(pageValue)
  if (page === null) {
    return {
      title: "Página inválida | Tops do Job",
      robots: buildPublicRobotsMetadata(false),
    }
  }
  try {
    const { data, estadoDescoberto } = await carregarEstado(estado, page)
    if (isPublicPageOutOfRange(page, data.paginacao)) {
      return {
        title: "Página inválida | Tops do Job",
        robots: buildPublicRobotsMetadata(false),
      }
    }
    const estadoUf = data.localidade.uf
    const estadoNome = data.localidade.estado || getEstadoNomePorUf(estadoUf)
    const canonicalUrl = buildPublicUrl(buildPublicPath("acompanhantes", estado), page)
    const title = gerarTituloSeoEstado({ estadoNome, estadoUf, page })
    const description = gerarDescricaoSeoEstado({
      estadoNome,
      totalAnuncios: data.paginacao.totalItens,
      totalCidades: estadoDescoberto.cidades.length,
      page,
    })
    return {
      title,
      description,
      alternates: { canonical: canonicalUrl },
      openGraph: { title, description, url: canonicalUrl, type: "website", siteName: "Tops do Job", locale: "pt_BR" },
      robots: buildPublicRobotsMetadata(
        isCleanPublicFirstPage(pageValue, page) &&
          estadoDescoberto.indexacao.indexavel &&
          estadoDescoberto.indexacao.canonica,
        true
      ),
    }
  } catch (error) {
    if (isPublicCatalogNotFound(error)) {
      return {
        title: "Estado não encontrado | Tops do Job",
        robots: buildPublicRobotsMetadata(false),
      }
    }
    throw error
  }
}

export default async function EstadoPage({ params, searchParams }: PageProps) {
  const { estado } = await params
  const page = parsePublicPage((await searchParams).page)
  if (page === null) notFound()

  let carregado
  try {
    carregado = await carregarEstado(estado, page)
  } catch (error) {
    if (isPublicCatalogNotFound(error)) notFound()
    throw error
  }
  const { data, estadoDescoberto } = carregado
  if (isPublicPageOutOfRange(page, data.paginacao)) notFound()
  const estadoUf = data.localidade.uf
  const estadoNome = data.localidade.estado || getEstadoNomePorUf(estadoUf)

  const h1 = `Acompanhantes em ${estadoNome} – ${estadoUf}`
  const descricaoTopo = `Encontre acompanhantes em ${estadoNome}. Veja perfis ativos por cidade, com fotos nos anúncios, contato direto e navegação local.`
  const seoContent = gerarConteudoSeoEstado(estadoNome, estadoUf)

  const baseUrl = getPublicSiteBaseUrl()
  const estadoPath = buildPublicPath("acompanhantes", estado)
  const breadcrumbSchema = gerarBreadcrumbSchemaEstado(baseUrl, estadoUf)

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

  const cidadesOrdenadas = [...estadoDescoberto.cidades]
    .sort(
      (a, b) =>
        Number(isCidadeIndexavelLocal(b)) - Number(isCidadeIndexavelLocal(a)) ||
        a.nome.localeCompare(b.nome)
    )
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
        <h1 className="break-normal text-3xl font-bold text-gray-900 sm:text-4xl">{h1}</h1>
        <p className="text-lg text-gray-600">{descricaoTopo}</p>
      </div>

      <StoriesBar />

      <ListagemPublicaPaginada
        key={data.paginacao.ordemSeed}
        caminhoBase={estadoPath}
        escopo={{ tipo: "estado", uf: estado }}
        initialData={data}
      >
        {page === 0 && (
          <section className="prose prose-sm max-w-none text-gray-700 space-y-4">
            <div dangerouslySetInnerHTML={{ __html: seoContent }} />
          </section>
        )}
      </ListagemPublicaPaginada>

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
                key={cidadeItem.slug}
                href={buildPublicPath("acompanhantes", estado, cidadeItem.slug)}
                className="px-4 py-2 bg-pink-100 text-pink-700 rounded-lg hover:bg-pink-200 transition text-center text-sm font-medium"
              >
                {labelAcompanhantesCidade(cidadeItem.nome)}
              </Link>
            ))}
          </div>
        </section>
      )}

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: serializeJsonLd(breadcrumbSchema) }}
      />

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
