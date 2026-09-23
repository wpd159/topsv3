import type { Metadata } from "next"
import Link from "next/link"
import Image from "next/image"
import { notFound } from "next/navigation"
import { cache } from "react"
import { ArrowRightIcon } from "@heroicons/react/24/solid"
import { ApiContractError } from "@/lib/api-contract"
import {
  fetchPublicBlogCategorias,
  fetchPublicBlogPostsByCategoria,
} from "@/lib/blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import { buildPublicPath, buildPublicUrl } from "@/lib/seo/public-url"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"

const FALLBACK_IMAGE = getPublicLogoUrl()

const loadPublicBlogCategory = cache(async (rawSlug: string) => {
  const slug = rawSlug.trim().toLowerCase()
  const categories = await fetchPublicBlogCategorias()
  const category = categories.find(
    (candidate) => candidate.ativa && candidate.slug === slug,
  )
  if (!category) return null

  try {
    const posts = await fetchPublicBlogPostsByCategoria(category.slug)
    return {
      category,
      posts: posts.filter((post) => post.status === "PUBLICADO"),
    }
  } catch (error) {
    if (error instanceof ApiContractError && error.status === 404) return null
    throw error
  }
})

async function resolvePublicBlogCategory(slug: string) {
  const result = await loadPublicBlogCategory(slug)
  if (!result) notFound()
  return result
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const { category, posts } = await resolvePublicBlogCategory(slug)
  const canonical = buildPublicUrl(
    buildPublicPath("blog", "categoria", category.slug),
  )
  const title = `${category.nome} | Blog Tops do Job`
  const description = `Posts e guias na categoria ${category.nome}.`

  return {
    title,
    description,
    alternates: { canonical },
    robots: buildPublicRobotsMetadata(posts.length > 0),
    openGraph: {
      title,
      description,
      url: canonical,
      type: "website",
    },
  }
}

export default async function BlogCategoriaPage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  const { category, posts } = await resolvePublicBlogCategory(slug)

  return (
    <main className="min-h-screen bg-white px-6 pb-20 pt-12">
      <div className="mx-auto max-w-4xl">
        <nav className="mb-8 text-sm text-gray-500">
          <Link href="/blog" className="text-[#FC1EAD] hover:underline">
            Blog
          </Link>
          <span className="mx-2">/</span>
          <span className="text-gray-800">{category.nome}</span>
        </nav>

        <h1 className="text-3xl font-bold text-gray-900 md:text-4xl">
          {category.nome}
        </h1>
        <p className="mt-2 text-sm text-gray-600">Posts editoriais nesta categoria.</p>

        <section className="mt-10">
          <h2 className="text-xl font-semibold text-gray-900">Posts</h2>
          {posts.length === 0 ? (
            <p className="mt-4 rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
              Nenhum post editorial nesta categoria.
            </p>
          ) : (
            <div className="mt-6 flex flex-col gap-6">
              {posts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${encodeURIComponent(post.slug)}`}
                  className="group flex flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white transition-all hover:shadow-md md:flex-row"
                >
                  <div className="relative h-48 w-full md:h-auto md:w-2/5">
                    <Image
                      src={post.imagemUrl || FALLBACK_IMAGE}
                      alt={post.titulo}
                      fill
                      sizes="(min-width: 1024px) 360px, (min-width: 768px) 40vw, calc(100vw - 48px)"
                      quality={85}
                      unoptimized={!post.imagemUrl}
                      className="h-full w-full rounded-t-2xl object-cover md:rounded-l-2xl md:rounded-tr-none"
                    />
                    <div className="absolute left-3 top-3 rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white">
                      {post.categoria}
                    </div>
                  </div>
                  <div className="flex flex-1 flex-col justify-between p-6">
                    <div>
                      <h3 className="text-lg font-semibold text-gray-900 transition group-hover:text-[#FC1EAD]">
                        {post.titulo}
                      </h3>
                      <p className="mt-2 line-clamp-3 text-sm text-gray-600">
                        {post.resumo}
                      </p>
                    </div>
                    <span className="mt-4 inline-flex w-fit items-center text-sm font-medium text-[#FC1EAD]">
                      Ler mais
                      <ArrowRightIcon className="ml-1 h-4 w-4" />
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </section>

        <p className="mt-12 text-center text-sm text-gray-500">
          <Link href="/blog" className="text-[#FC1EAD] hover:underline">
            Voltar ao blog
          </Link>
        </p>
      </div>
    </main>
  )
}
