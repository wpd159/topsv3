import type { Metadata } from "next"
import { BlogContent } from "@/components/blog/blog-content"
import { fetchPublicBlogCategorias, fetchPublicBlogPosts } from "@/lib/blog-api"
import { buildPublicUrl } from "@/lib/seo/public-url"

export const dynamic = "force-dynamic"

export const metadata: Metadata = {
  title: "Blog | Tops do Job",
  description: "Conteúdo institucional, guias e novidades do Tops do Job.",
  alternates: {
    canonical: buildPublicUrl("/blog"),
  },
  openGraph: {
    title: "Blog | Tops do Job",
    description: "Conteúdo institucional, guias e novidades do Tops do Job.",
    url: buildPublicUrl("/blog"),
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
}

export default async function BlogHomePage() {
  const [posts, categories] = await Promise.all([
    fetchPublicBlogPosts(),
    fetchPublicBlogCategorias(),
  ])

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
        initialPosts={posts}
        initialCategories={categories}
        initialLoaded
      />
    </main>
  )
}
