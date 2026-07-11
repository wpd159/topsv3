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
import { selecionarCapaPublicaSegura, type MidiaPublica } from "@/lib/media/public-media"

const SAFE_COMPLIANCE_IMAGE_ABSOLUTE = buildPublicUrl("/2151117281.jpg")

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

function resolvePublicSeoImage(url?: string | null) {
  return isComplianceAssetUrl(url) ? SAFE_COMPLIANCE_IMAGE_ABSOLUTE : url || SAFE_COMPLIANCE_IMAGE_ABSOLUTE
}

async function loadInitialAnuncio(slug: string) {
  return obterAnuncioPublicoPorSlug(slug)
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
    const capa = selecionarCapaPublicaSegura(data?.midias as MidiaPublica[] | undefined)
    const imagem = resolvePublicSeoImage(capa?.urlPublica)
    const indexavel = data.indexavelSeo

    const title = gerarTituloSeoAnuncio({
      titulo,
      cidadeNome: data?.cidadeNome,
      bairroNome: data?.bairroNome,
    })

    return {
      title,
      description: descricao,
      alternates: {
        canonical: url,
      },
      robots: {
        index: indexavel,
        follow: true,
        "max-image-preview": "large",
        "max-snippet": -1,
        "max-video-preview": -1,
      },
      openGraph: {
        title,
        description: descricao,
        url,
        siteName: "Tops do Job",
        images: [{ url: imagem, width: 1200, height: 630, alt: titulo }],
        type: "website",
      },
      twitter: {
        card: "summary_large_image",
        title,
        description: descricao,
        images: [imagem],
      },
    }
  } catch {
    return {
      title: "Anúncio não encontrado | Tops do Job",
      description: "O anúncio solicitado não está disponível.",
      robots: {
        index: false,
        follow: true,
      },
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
    const relacionados = initialData.cidadeSlug
      ? await listarPublicosPorCidade(initialData.estadoUf, initialData.cidadeSlug, 0, 16)
      : null
    return <AnuncioDetalhesPageClient initialData={initialData} initialRelatedData={relacionados?.itens ?? []} />
  } catch (error) {
    if (isPublicCatalogNotFound(error)) {
      notFound()
    }

    throw error
  }
}
