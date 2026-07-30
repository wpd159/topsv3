import type { Metadata } from "next"
import { notFound } from "next/navigation"
import AnuncioDetalhesPageClient from "./anuncio-detalhes"
import { gerarDescricaoSeoAnuncio, gerarTituloSeoAnuncio } from "@/lib/seo/public-metadata"
import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import {
  isPublicCatalogNotFound,
  listarPublicosPorCidade,
  obterAnuncioPublicoPorSlug,
} from "@/lib/public-catalog-api"
import { buildPublicPath, buildPublicUrl } from "@/lib/seo/public-url"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"
import {
  fontePublicaSegura,
  selecionarCapaPublicaSegura,
  type MidiaPublica,
} from "@/lib/media/public-media"

function isComplianceAssetUrl(url?: string | null) {
  if (!url) return false

  try {
    const parsed = new URL(url, buildPublicUrl("/"))
    return (
      parsed.hostname.includes("backend.topsdojob.com") ||
      parsed.pathname.includes("/compliance/assets/")
    )
  } catch {
    return url.includes("/compliance/assets/")
  }
}

async function loadInitialAnuncio(slug: string) {
  return obterAnuncioPublicoPorSlug(slug)
}

function selecionarImagemPublicaSeo(midias?: MidiaPublica[]) {
  const capa = selecionarCapaPublicaSegura(midias)
  const fonte = capa ? fontePublicaSegura(capa) : null
  return fonte && !isComplianceAssetUrl(fonte) ? fonte : undefined
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const url = buildPublicUrl(buildPublicPath("anuncios", slug))

  try {
    const data = await loadInitialAnuncio(slug)
    const titulo = corrigirTextoCorrompido(data.titulo) || "Anúncio"
    const descricao = gerarDescricaoSeoAnuncio({
      titulo,
      descricao: data?.descricao,
      cidadeNome: data?.cidadeNome,
      bairroNome: data?.bairroNome,
    })
    const imagemPublica = selecionarImagemPublicaSeo(data?.midias as MidiaPublica[] | undefined)
    const indexavel = data.indexavelSeo

    const title = gerarTituloSeoAnuncio({
      titulo,
      cidadeNome: data?.cidadeNome,
      bairroNome: data?.bairroNome,
      categoria: data?.categoria,
    })

    return {
      title,
      description: descricao,
      alternates: {
        canonical: url,
      },
      robots: buildPublicRobotsMetadata(indexavel, true),
      openGraph: {
        title,
        description: descricao,
        url,
        siteName: "Tops do Job",
        ...(imagemPublica
          ? { images: [{ url: imagemPublica, width: 1200, height: 630, alt: titulo }] }
          : {}),
        type: "website",
      },
      twitter: {
        card: "summary_large_image",
        title,
        description: descricao,
        ...(imagemPublica ? { images: [imagemPublica] } : {}),
      },
    }
  } catch {
    return {
      title: "Anúncio não encontrado | Tops do Job",
      description: "O anúncio solicitado não está disponível.",
      robots: buildPublicRobotsMetadata(false),
    }
  }
}

export default async function Page({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  try {
    const { slug } = await params
    const initialData = await loadInitialAnuncio(slug)
    const titulo = corrigirTextoCorrompido(initialData.titulo) || "Anúncio"
    const title = gerarTituloSeoAnuncio({
      titulo,
      cidadeNome: initialData?.cidadeNome,
      bairroNome: initialData?.bairroNome,
      categoria: initialData?.categoria,
    })
    const canonicalUrl = buildPublicUrl(buildPublicPath("anuncios", slug))
    const imagemPublica = selecionarImagemPublicaSeo(
      initialData?.midias as MidiaPublica[] | undefined,
    )
    const relacionados = initialData.cidadeSlug
      ? await listarPublicosPorCidade(initialData.estadoUf, initialData.cidadeSlug, 0, 16)
      : null
    const webPageJsonLd = {
      "@context": "https://schema.org",
      "@type": "WebPage",
      url: canonicalUrl,
      name: title,
      ...(imagemPublica
        ? {
            primaryImageOfPage: {
              "@type": "ImageObject",
              url: imagemPublica,
            },
          }
        : {}),
    }

    return (
      <>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(webPageJsonLd).replace(/</g, "\\u003c"),
          }}
        />
        <AnuncioDetalhesPageClient
          initialData={initialData}
          initialRelatedData={relacionados?.itens ?? []}
        />
      </>
    )
  } catch (error) {
    if (isPublicCatalogNotFound(error)) {
      notFound()
    }

    throw error
  }
}
