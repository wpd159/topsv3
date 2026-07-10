import type { Metadata } from "next"
import { notFound } from "next/navigation"
import {
  fetchProgrammaticBlogPage,
  ProgrammaticBlogAmbiguousError,
} from "@/lib/programmatic-blog-api"
import ProgrammaticBlogPageClient from "./programmatic-blog-page-client"
import { getPublicSiteBaseUrl } from "@/lib/seo/public-url"

export const revalidate = 3600

export async function generateMetadata({
  params,
  searchParams,
}: {
  params: Promise<{ tema: string; cidade: string }>
  searchParams: Promise<{ uf?: string }>
}): Promise<Metadata> {
  const { tema, cidade } = await params
  const { uf } = await searchParams

  try {
    const page = await fetchProgrammaticBlogPage(tema, cidade, uf)
    if (!page) {
      return { title: "Página não encontrada | Blog Tops do Job" }
    }

    const canonical = `${getPublicSiteBaseUrl()}${page.canonicalPath}`
    return {
      title: page.title,
      description: page.metaDescription || undefined,
      alternates: { canonical },
      robots: page.indexed
        ? { index: true, follow: true }
        : { index: false, follow: true },
      openGraph: {
        title: page.title,
        description: page.metaDescription || undefined,
        url: canonical,
        type: "article",
      },
    }
  } catch (e) {
    if (e instanceof ProgrammaticBlogAmbiguousError) {
      return { title: "Selecione o estado | Blog Tops do Job", robots: { index: false, follow: true } }
    }
    return { title: "Blog Tops do Job" }
  }
}

export default async function ProgrammaticBlogCityPage({
  params,
  searchParams,
}: {
  params: Promise<{ tema: string; cidade: string }>
  searchParams: Promise<{ uf?: string }>
}) {
  const { tema, cidade } = await params
  const { uf } = await searchParams

  let page: Awaited<ReturnType<typeof fetchProgrammaticBlogPage>>
  try {
    page = await fetchProgrammaticBlogPage(tema, cidade, uf)
  } catch (err) {
    if (err instanceof ProgrammaticBlogAmbiguousError) {
      const sampleUf = err.ufs && err.ufs.length > 0 ? err.ufs[0] : "GO"
      return (
        <main className="min-h-screen bg-white px-6 py-16">
          <h1 className="text-2xl font-bold text-gray-900">Cidade ambígua</h1>
          <p className="mt-4 text-gray-700">
            Existe mais de uma cidade com este nome no cadastro. Adicione o parâmetro{" "}
            <code className="rounded bg-gray-100 px-1">uf</code> na URL (ex.:{" "}
            <code className="rounded bg-gray-100 px-1">?uf={sampleUf}</code>).
          </p>
        </main>
      )
    }
    notFound()
  }

  if (!page) notFound()

  const base = getPublicSiteBaseUrl()
  const canonical = `${base}${page.canonicalPath}`

  const breadcrumbLd = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      {
        "@type": "ListItem",
        position: 1,
        name: "Início",
        item: `${base}/`,
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "Blog",
        item: `${base}/blog`,
      },
      {
        "@type": "ListItem",
        position: 3,
        name: page.h1,
        item: canonical,
      },
    ],
  }

  const webPageLd = {
    "@context": "https://schema.org",
    "@type": "WebPage",
    name: page.title,
    description: page.metaDescription || undefined,
    url: canonical,
    inLanguage: "pt-BR",
    isPartOf: { "@type": "WebSite", name: "Tops do Job", url: base },
  }

  const faqLd =
    page.faq.length > 0
      ? {
          "@context": "https://schema.org",
          "@type": "FAQPage",
          mainEntity: page.faq.map((f) => ({
            "@type": "Question",
            name: f.pergunta,
            acceptedAnswer: {
              "@type": "Answer",
              text: f.resposta,
            },
          })),
        }
      : null

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(breadcrumbLd) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(webPageLd) }}
      />
      {faqLd ? (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(faqLd) }}
        />
      ) : null}
      <ProgrammaticBlogPageClient data={page} />
    </>
  )
}
