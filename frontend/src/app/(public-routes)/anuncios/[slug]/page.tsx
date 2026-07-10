import type { Metadata } from "next"
import { notFound } from "next/navigation"
import AnuncioDetalhesPageClient from "./anuncio-detalhes"
import { gerarDescricaoSeoAnuncio, gerarTituloSeoAnuncio } from "@/lib/seo/public-metadata"
import { shouldIndexAnuncio } from "@/lib/seo/anuncio-indexing"
import { corrigirTextoCorrompido } from "@/lib/text/encoding"
import { ServerApiError, serverApiFetchJson } from "@/lib/server-api"
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
  const apiUrl = process.env.NEXT_PUBLIC_API_URL
  if (!apiUrl) return null

  return serverApiFetchJson<any>(`${apiUrl}/anuncios/publico/slug/${encodeURIComponent(slug)}`, {
    next: { revalidate: 0 },
  })
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
    if (!data) throw new Error("Falha ao buscar anúncio")

    const titulo = corrigirTextoCorrompido(data?.titulo || data?.username) || "Anúncio"
    const descricao = gerarDescricaoSeoAnuncio({
      titulo,
      descricao: data?.descricaoAnuncio || data?.descricao,
      cidadeNome: data?.cidadeNome,
      bairroNome: data?.bairroNome,
    })
    const capa = selecionarCapaPublicaSegura(data?.midias as MidiaPublica[] | undefined)
    const imagem = resolvePublicSeoImage(capa?.urlPublica)
    const indexavel = shouldIndexAnuncio(data)

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
    if (!initialData) {
      notFound()
    }

    return <AnuncioDetalhesPageClient initialData={initialData} />
  } catch (error) {
    if (error instanceof ServerApiError && error.status === 404) {
      notFound()
    }

    throw error
  }
}
