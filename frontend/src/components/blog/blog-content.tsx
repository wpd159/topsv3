import Link from "next/link"
import Image from "next/image"
import { ArrowRightIcon } from "@heroicons/react/24/solid"
import type { BlogCategoriaPublic, BlogPostSummary } from "@/lib/blog-api"
import { slugifyCategoria } from "@/lib/blog-categories"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

const FALLBACK_IMAGE = getPublicLogoUrl()

function resolveCategoriaSlug(post: BlogPostSummary): string {
  return (
    (post.categoriaSlug && String(post.categoriaSlug)) ||
    slugifyCategoria(post.categoria || "")
  )
    .trim()
    .toLowerCase()
}

function isLegacyCityGuidePost(post: BlogPostSummary): boolean {
  return resolveCategoriaSlug(post).endsWith("-por-cidades")
}

export function BlogContent({
  posts,
  categories,
}: {
  posts: BlogPostSummary[]
  categories: BlogCategoriaPublic[]
}) {
  const safePosts = posts.filter((post) => post?.status === "PUBLICADO")
  const publicCategories = categories.filter((category) => category?.ativa)
  const legacyGuidePosts = safePosts.filter(isLegacyCityGuidePost)
  const editorialPosts = safePosts.filter((post) => !isLegacyCityGuidePost(post))
  const latestPosts = safePosts.slice(0, 3)

  return (
    <section className="mb-20 mt-12 grid grid-cols-1 gap-8 px-6 md:grid-cols-3">
      <div className="flex flex-col gap-10 md:col-span-2">
        {legacyGuidePosts.length > 0 ? (
          <section className="rounded-2xl border border-gray-200 bg-gray-50/60 p-6">
            <h2 className="text-2xl font-bold text-gray-900">Guias por cidade</h2>
            <p className="mt-2 text-sm text-gray-600">
              Conteúdo editorial por localidade — consulte anúncios e publicações conforme cada região.
            </p>
            <ul className="mt-6 grid gap-3 sm:grid-cols-2">
              {legacyGuidePosts.map((post) => (
                <li key={post.id}>
                  <Link
                    href={`/blog/${encodeURIComponent(post.slug)}`}
                    className="block rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm transition hover:border-[#FC1EAD]/40 hover:shadow-sm"
                  >
                    <span className="font-medium text-gray-900">{post.titulo}</span>
                    <span className="mt-1 block text-xs text-gray-500">
                      {post.categoria || "Guia por cidade"}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <div>
          <h2 className="text-3xl font-bold text-gray-900">Posts editoriais</h2>
          <p className="mt-2 text-sm text-gray-500">
            Artigos, notícias e conteúdo produzido pela equipe.
          </p>
        </div>

        {editorialPosts.map((post) => {
          const categoriaSlug = resolveCategoriaSlug(post)
          const categoriaHref =
            post.categoria?.trim() && categoriaSlug
              ? `/blog/categoria/${encodeURIComponent(categoriaSlug)}`
              : null

          return (
            <article
              key={post.id}
              className="group flex flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white transition-all hover:shadow-md md:flex-row"
            >
              <div className="relative h-56 w-full md:h-auto md:w-1/2">
                <Link href={`/blog/${encodeURIComponent(post.slug)}`} className="block h-full">
                  <Image
                    src={post.imagemUrl || FALLBACK_IMAGE}
                    alt={post.titulo}
                    fill
                    sizes="(min-width: 768px) 33vw, calc(100vw - 48px)"
                    quality={85}
                    unoptimized={!post.imagemUrl}
                    className="h-full w-full rounded-t-2xl object-cover md:rounded-l-2xl md:rounded-tr-none"
                  />
                </Link>
                <div className="pointer-events-none absolute left-3 top-3">
                  {categoriaHref ? (
                    <Link
                      href={categoriaHref}
                      className="pointer-events-auto inline-block rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white transition hover:bg-[#d91992]"
                    >
                      {post.categoria}
                    </Link>
                  ) : (
                    <span className="inline-block rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white">
                      {post.categoria || "Sem categoria"}
                    </span>
                  )}
                </div>
              </div>

              <div className="flex flex-1 flex-col justify-between p-6">
                <Link href={`/blog/${encodeURIComponent(post.slug)}`} className="block">
                  <h3 className="mb-2 text-lg font-semibold text-gray-900 transition group-hover:text-[#FC1EAD]">
                    {post.titulo}
                  </h3>
                  <p className="mb-3 line-clamp-3 text-sm text-gray-600">{post.resumo}</p>
                </Link>

                <div className="flex items-center justify-between text-xs text-gray-500">
                  <span>{post.autorNome}</span>
                  <span>
                    {post.publishedAt
                      ? new Date(post.publishedAt).toLocaleDateString("pt-BR")
                      : "Rascunho"}
                  </span>
                </div>

                <Link
                  href={`/blog/${encodeURIComponent(post.slug)}`}
                  className="mt-3 inline-flex w-fit items-center rounded-2xl px-3 py-2 text-sm font-medium text-[#FC1EAD] transition group-hover:bg-[#FC1EAD]/10"
                >
                  Ler mais
                  <ArrowRightIcon className="ml-1 h-4 w-4" />
                </Link>
              </div>
            </article>
          )
        })}

        {editorialPosts.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
            Nenhum post publicado no momento.
          </div>
        ) : null}
      </div>

      <aside className="space-y-8">
        <div className="rounded-2xl border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Últimos posts</h3>
          {latestPosts.length > 0 ? (
            <div className="space-y-4">
              {latestPosts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${encodeURIComponent(post.slug)}`}
                  className="group flex items-center gap-3 border-b border-gray-200 pb-3 last:border-0"
                >
                  <div className="relative h-12 w-12 flex-shrink-0">
                    <Image
                      src={post.imagemUrl || FALLBACK_IMAGE}
                      alt={post.titulo}
                      width={48}
                      height={48}
                      quality={85}
                      unoptimized={!post.imagemUrl}
                      className="h-12 w-12 rounded-md object-cover"
                    />
                  </div>
                  <div>
                    <p className="line-clamp-1 text-sm font-medium text-gray-800 transition group-hover:text-[#FC1EAD]">
                      {post.titulo}
                    </p>
                    <p className="mt-0.5 text-[11px] font-semibold text-[#FC1EAD]">Ler mais →</p>
                  </div>
                </Link>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">Nenhum post publicado.</p>
          )}
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Categorias</h3>
          {publicCategories.length > 0 ? (
            <ul className="space-y-3 text-sm">
              {publicCategories.map((category) => (
                <li
                  key={category.id}
                  className="flex justify-between border-b border-gray-100 pb-2 last:border-0"
                >
                  <Link
                    href={`/blog/categoria/${encodeURIComponent(category.slug)}`}
                    className="text-gray-700 transition hover:text-[#FC1EAD]"
                  >
                    {category.nome}
                  </Link>
                  <span className="font-semibold text-[#FC1EAD]">
                    {category.postCountPublicados}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-gray-500">Sem categorias publicadas.</p>
          )}
        </div>
      </aside>
    </section>
  )
}
