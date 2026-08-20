import type { Metadata } from "next"
import { cache } from "react"
import { BlogContent } from "@/components/blog/blog-content"
import { fetchPublicBlogCategorias, fetchPublicBlogPosts } from "@/lib/blog-api"
import { buildPublicUrl } from "@/lib/seo/public-url"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"

export const dynamic = "force-dynamic"

const BLOG_DESCRIPTION = "Conteúdo institucional, guias e novidades do Tops do Job."

const loadBlogHome = cache(async () => {
  const [posts, categories] = await Promise.all([
    fetchPublicBlogPosts(),
    fetchPublicBlogCategorias(),
  ])

  return {
    posts: posts.filter((post) => post.status === "PUBLICADO"),
    categories: categories.filter((category) => category.ativa),
  }
})

export async function generateMetadata(): Promise<Metadata> {
  const { posts } = await loadBlogHome()
  const canonical = buildPublicUrl("/blog")

  return {
    title: "Blog | Tops do Job",
    description: BLOG_DESCRIPTION,
    alternates: { canonical },
    robots: buildPublicRobotsMetadata(posts.length > 0),
    openGraph: {
      title: "Blog | Tops do Job",
      description: BLOG_DESCRIPTION,
      url: canonical,
      type: "website",
      siteName: "Tops do Job",
      locale: "pt_BR",
    },
  }
}

export default async function BlogHomePage() {
  const { posts, categories } = await loadBlogHome()

  return (
    <main className="min-h-screen bg-white">
      <header className="mx-auto max-w-7xl px-6 pt-10">
        <h1 className="text-3xl font-bold text-gray-900 md:text-4xl">
          Blog Tops do Job
        </h1>
        <p className="mt-2 max-w-3xl text-gray-600">
          Guias, novidades e conteúdo editorial para uma experiência mais segura e informada.
        </p>
      </header>
      <BlogContent
        posts={posts}
        categories={categories}
      />
    </main>
  )
}
