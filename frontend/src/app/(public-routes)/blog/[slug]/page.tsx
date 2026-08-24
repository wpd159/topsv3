import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { cache } from "react"
import { ApiContractError } from "@/lib/api-contract"
import { fetchPublicBlogPost } from "@/lib/blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { buildPublicPath, buildPublicUrl } from "@/lib/seo/public-url"
import BlogPostPageClient from "./blog-post-page-client"

const loadPublicBlogPost = cache(fetchPublicBlogPost)

async function resolvePost(slug: string) {
  try {
    const post = await loadPublicBlogPost(slug)
    if (post.status !== "PUBLICADO") notFound()
    return post
  } catch (error) {
    if (
      error instanceof ApiContractError &&
      (error.status === 400 || error.status === 404)
    ) {
      notFound()
    }
    throw error
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const post = await resolvePost(slug)
  const canonical = buildPublicUrl(buildPublicPath("blog", post.slug))
  const image = post.ogImageUrl || post.imagemUrl || getPublicLogoUrl()

  return {
    title: post.seoTitle || `${post.titulo} | Tops do Job Blog`,
    description: post.seoDescription || post.resumo,
    alternates: { canonical },
    openGraph: {
      title: post.seoTitle || post.titulo,
      description: post.seoDescription || post.resumo,
      url: canonical,
      type: "article",
      siteName: "Tops do Job",
      locale: "pt_BR",
      publishedTime: post.publishedAt || undefined,
      modifiedTime: post.updatedAt || undefined,
      images: [{ url: image, alt: post.titulo }],
    },
  }
}

export default async function BlogPostPage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  const post = await resolvePost(slug)
  const canonical = buildPublicUrl(buildPublicPath("blog", post.slug))
  const image = post.ogImageUrl || post.imagemUrl || getPublicLogoUrl()
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "BlogPosting",
    headline: post.titulo,
    description: post.seoDescription || post.resumo,
    url: canonical,
    mainEntityOfPage: canonical,
    image,
    author: { "@type": "Organization", name: post.autorNome },
    publisher: {
      "@type": "Organization",
      name: "Tops do Job",
      logo: { "@type": "ImageObject", url: getPublicLogoUrl() },
    },
    datePublished: post.publishedAt,
    dateModified: post.updatedAt || post.publishedAt,
  }

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: serializeJsonLd(jsonLd) }}
      />
      <BlogPostPageClient post={post} />
    </>
  )
}
