import type { Metadata } from "next"
import { fetchPublicBlogPost } from "@/lib/blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import BlogPostPageClient from "./blog-post-page-client"

function siteBase() {
  return (process.env.NEXT_PUBLIC_SITE_URL || "https://topsdojob.com").replace(/\/$/, "")
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const post = await fetchPublicBlogPost(slug).catch(() => null)

  if (!post) {
    return {
      title: "Post não encontrado | Tops do Job Blog",
    }
  }

  const canonical = `${siteBase()}/blog/${encodeURIComponent(slug)}`

  return {
    title: post.seoTitle || `${post.titulo} | Tops do Job Blog`,
    description: post.seoDescription || post.resumo || undefined,
    alternates: {
      canonical,
    },
    openGraph: {
      title: post.seoTitle || post.titulo,
      description: post.seoDescription || post.resumo || undefined,
      url: canonical,
      type: "article",
      images: [post.ogImageUrl || post.imagemUrl || getPublicLogoUrl()],
    },
  }
}

export default async function BlogPostPage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  const post = await fetchPublicBlogPost(slug).catch(() => null)

  return <BlogPostPageClient slug={slug} initialPost={post} />
}
