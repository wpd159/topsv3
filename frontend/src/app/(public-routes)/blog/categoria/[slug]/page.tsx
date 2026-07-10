import type { Metadata } from "next"
import Link from "next/link"
import { ArrowRightIcon } from "@heroicons/react/24/solid"
import { fetchPublicBlogPostsByCategoria, type BlogPostSummary } from "@/lib/blog-api"
import {
  fetchProgrammaticHomeEntries,
  programmaticBlogPageUrl,
  PROGRAMMATIC_BLOG_TEMAS,
} from "@/lib/programmatic-blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import { buildPublicPath, buildPublicUrl } from "@/lib/seo/public-url"

const FALLBACK_IMAGE = getPublicLogoUrl()

function isProgrammaticTemaSlug(slug: string): boolean {
  return (PROGRAMMATIC_BLOG_TEMAS as readonly string[]).includes(slug)
}

function titleCaseFromSlug(slug: string): string {
  return slug
    .split("-")
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ")
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>
}): Promise<Metadata> {
  const { slug } = await params
  const label = titleCaseFromSlug(slug)
  const canonical = buildPublicUrl(buildPublicPath("blog", "categoria", slug))
  return {
    title: `${label} | Blog Tops do Job`,
    description: `Posts e guias na categoria ${label}.`,
    alternates: { canonical },
    openGraph: {
      title: `${label} | Blog Tops do Job`,
      description: `Posts e guias na categoria ${label}.`,
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
  const decoded = decodeURIComponent(slug)

  let posts: BlogPostSummary[] = []
  try {
    posts = await fetchPublicBlogPostsByCategoria(decoded)
  } catch {
    posts = []
  }

  const showProgrammatic = isProgrammaticTemaSlug(decoded)
  const guias = showProgrammatic ? await fetchProgrammaticHomeEntries(36, decoded) : []

  const heading = titleCaseFromSlug(decoded)

  return (
    <main className="min-h-screen bg-white px-6 pb-20 pt-12">
      <div className="mx-auto max-w-4xl">
        <nav className="mb-8 text-sm text-gray-500">
          <Link href="/blog" className="text-[#FC1EAD] hover:underline">
            Blog
          </Link>
          <span className="mx-2">/</span>
          <span className="text-gray-800">{heading}</span>
        </nav>

        <h1 className="text-3xl font-bold text-gray-900 md:text-4xl">{heading}</h1>
        <p className="mt-2 text-sm text-gray-600">
          Posts editoriais{showProgrammatic ? " e guias por cidade relacionados ao tema." : " nesta categoria."}
        </p>

        {guias.length > 0 ? (
          <section className="mt-10 rounded-2xl border border-gray-200 bg-gray-50/80 p-6">
            <h2 className="text-xl font-semibold text-gray-900">Guias por cidade</h2>
            <p className="mt-1 text-sm text-gray-600">
              Páginas informativas por localidade (conteúdo editorial do site).
            </p>
            <ul className="mt-4 grid gap-3 sm:grid-cols-2">
              {guias.map((g) => (
                <li key={`${g.tema}-${g.cidadeSlug}-${g.estadoUf}`}>
                  <Link
                    href={programmaticBlogPageUrl(g.tema, g.cidadeSlug, g.estadoUf)}
                    className="block rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm transition hover:border-[#FC1EAD]/40 hover:shadow-sm"
                  >
                    <span className="font-medium text-gray-900">{g.title}</span>
                    <span className="mt-1 block text-xs text-gray-500">
                      {g.cidadeNome} — {g.estadoUf}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <section className="mt-10">
          <h2 className="text-xl font-semibold text-gray-900">Posts</h2>
          {posts.length === 0 ? (
            <p className="mt-4 rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
              Nenhum post editorial nesta categoria.
              {showProgrammatic && guias.length === 0
                ? " Não há guias publicados para este tema no momento."
                : null}
            </p>
          ) : (
            <div className="mt-6 flex flex-col gap-6">
              {posts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${post.slug}`}
                  className="group flex flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white transition-all hover:shadow-md md:flex-row"
                >
                  <div className="relative h-48 w-full md:h-auto md:w-2/5">
                    <img
                      src={post.imagemUrl || FALLBACK_IMAGE}
                      alt={post.titulo}
                      className="h-full w-full rounded-t-2xl object-cover md:rounded-l-2xl md:rounded-tr-none"
                    />
                    <div className="absolute left-3 top-3 rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white">
                      {post.categoria || "Sem categoria"}
                    </div>
                  </div>
                  <div className="flex flex-1 flex-col justify-between p-6">
                    <div>
                      <h3 className="text-lg font-semibold text-gray-900 transition group-hover:text-[#FC1EAD]">
                        {post.titulo}
                      </h3>
                      <p className="mt-2 line-clamp-3 text-sm text-gray-600">{post.resumo}</p>
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
