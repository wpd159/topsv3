"use client"

import { useEffect, useMemo, useState } from "react"
import Link from "next/link"
import { ArrowRightIcon } from "@heroicons/react/24/solid"
import {
  fetchPublicBlogCategorias,
  fetchPublicBlogPosts,
  type BlogCategoriaPublic,
  type BlogPostSummary,
} from "@/lib/blog-api"
import { slugifyCategoria } from "@/lib/blog-categories"
import {
  fetchProgrammaticHomeEntries,
  programmaticBlogPageUrl,
  type ProgrammaticBlogHomeEntry,
} from "@/lib/programmatic-blog-api"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import { ContractState } from '@/components/feedback/contract-state'

const FALLBACK_IMAGE = getPublicLogoUrl()

type BlogGuideListItem = {
  href: string
  title: string
  description: string
  key: string
}

function resolveCategoriaSlug(post: BlogPostSummary): string {
  return ((post.categoriaSlug && String(post.categoriaSlug)) || slugifyCategoria(post.categoria || ""))
    .trim()
    .toLowerCase()
}

function isLegacyCityGuidePost(post: BlogPostSummary): boolean {
  return resolveCategoriaSlug(post).endsWith("-por-cidades")
}

export function BlogContent({ initialPosts = [] }: { initialPosts?: BlogPostSummary[] }) {
  const [posts, setPosts] = useState<BlogPostSummary[]>(Array.isArray(initialPosts) ? initialPosts : [])
  const [guias, setGuias] = useState<ProgrammaticBlogHomeEntry[]>([])
  const [loading, setLoading] = useState(initialPosts.length === 0)
  const [loadingGuias, setLoadingGuias] = useState(true)
  const [catalogCategorias, setCatalogCategorias] = useState<BlogCategoriaPublic[]>([])
  const [postsError, setPostsError] = useState<unknown>(null)
  const [guidesError, setGuidesError] = useState<unknown>(null)
  const [categoriesError, setCategoriesError] = useState<unknown>(null)
  const [retryVersion, setRetryVersion] = useState(0)

  useEffect(() => {
    let active = true

    async function loadPosts() {
      setPostsError(null)
      try {
        const data = await fetchPublicBlogPosts()
        if (active) {
          setPosts(data)
        }
      } catch (error) {
        if (active) {
          setPostsError(error)
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadPosts()

    return () => {
      active = false
    }
  }, [retryVersion])

  useEffect(() => {
    let active = true

    async function loadGuias() {
      setGuidesError(null)
      try {
        const data = await fetchProgrammaticHomeEntries(48)
        if (active) {
          setGuias(data)
        }
      } catch (error) {
        if (active) {
          setGuidesError(error)
        }
      } finally {
        if (active) {
          setLoadingGuias(false)
        }
      }
    }

    loadGuias()

    return () => {
      active = false
    }
  }, [retryVersion])

  useEffect(() => {
    let active = true

    async function loadCats() {
      setCategoriesError(null)
      try {
        const data = await fetchPublicBlogCategorias()
        if (active) {
          setCatalogCategorias(data)
        }
      } catch (error) {
        if (active) {
          setCategoriesError(error)
        }
      }
    }

    loadCats()

    return () => {
      active = false
    }
  }, [retryVersion])

  const safePosts = useMemo(() => (Array.isArray(posts) ? posts.filter(Boolean) : []), [posts])
  const legacyGuidePosts = useMemo(() => safePosts.filter((post) => isLegacyCityGuidePost(post)), [safePosts])
  const editorialPosts = useMemo(() => safePosts.filter((post) => !isLegacyCityGuidePost(post)), [safePosts])
  const guideItems = useMemo<BlogGuideListItem[]>(() => {
    const items: BlogGuideListItem[] = []
    const seen = new Set<string>()

    for (const guide of Array.isArray(guias) ? guias : []) {
      const href = programmaticBlogPageUrl(guide.tema, guide.cidadeSlug, guide.estadoUf)
      if (seen.has(href)) continue
      seen.add(href)
      items.push({
        href,
        title: guide.title,
        description: `${guide.temaLabel} - ${guide.cidadeNome} (${guide.estadoUf})`,
        key: `${guide.tema}-${guide.cidadeSlug}-${guide.estadoUf}`,
      })
    }

    for (const post of legacyGuidePosts) {
      const href = `/blog/${post.slug}`
      if (seen.has(href)) continue
      seen.add(href)
      items.push({
        href,
        title: post.titulo,
        description: post.categoria || "Guia por cidade",
        key: `blog-post-${post.id}`,
      })
    }

    return items
  }, [guias, legacyGuidePosts])
  const latestPosts = safePosts.slice(0, 3)
  const categoriasFallback = useMemo(() => {
    const categoriasMap = new Map<string, number>()
    for (const post of safePosts) {
      const categoria = post?.categoria?.trim() || "Sem categoria"
      categoriasMap.set(categoria, (categoriasMap.get(categoria) || 0) + 1)
    }
    return Array.from(categoriasMap.entries())
  }, [safePosts])

  return (
    <section className="mb-20 mt-12 grid grid-cols-1 gap-8 px-6 md:grid-cols-3">
      <div className="flex flex-col gap-10 md:col-span-2">
        <section className="rounded-2xl border border-gray-200 bg-gray-50/60 p-6">
          <h2 className="text-2xl font-bold text-gray-900">Guias por cidade</h2>
          <p className="mt-2 text-sm text-gray-600">
            Conteúdo editorial por localidade — consulte anúncios e publicações conforme cada região.
          </p>
          {loadingGuias ? (
            <div className="mt-6 rounded-xl border border-dashed border-gray-200 bg-white p-8 text-center text-sm text-gray-500">
              Carregando guias...
            </div>
          ) : guidesError ? (
            <div className="mt-6">
              <ContractState error={guidesError} onRetry={() => setRetryVersion((value) => value + 1)} />
            </div>
          ) : guideItems.length === 0 ? (
            <div className="mt-6 rounded-xl border border-dashed border-gray-200 bg-white p-8 text-center text-sm text-gray-500">
              Nenhum guia publicado no momento.
            </div>
          ) : (
            <ul className="mt-6 grid gap-3 sm:grid-cols-2">
              {guideItems.map((guide) => (
                <li key={guide.key}>
                  <Link
                    href={guide.href}
                    className="block rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm transition hover:border-[#FC1EAD]/40 hover:shadow-sm"
                  >
                    <span className="font-medium text-gray-900">{guide.title}</span>
                    <span className="mt-1 block text-xs text-gray-500">{guide.description}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div>
          <h2 className="text-3xl font-bold text-gray-900">Posts editoriais</h2>
          <p className="mt-2 text-sm text-gray-500">
            Artigos, notícias e conteúdo produzido pela equipe.
          </p>
        </div>

        {loading ? (
          <div className="rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
            Carregando posts do blog...
          </div>
        ) : postsError ? (
          <ContractState error={postsError} onRetry={() => setRetryVersion((value) => value + 1)} />
        ) : (
          editorialPosts.map((post) => {
            const catSlug =
              (post.categoriaSlug && String(post.categoriaSlug)) || slugifyCategoria(post.categoria || "")
            const catHref =
              post.categoria?.trim() && catSlug ? `/blog/categoria/${encodeURIComponent(catSlug)}` : null
            return (
              <article
                key={post.id}
                className="group flex flex-col overflow-hidden rounded-2xl border border-gray-200 bg-white transition-all hover:shadow-md md:flex-row"
              >
                <div className="relative h-56 w-full md:h-auto md:w-1/2">
                  <Link href={`/blog/${post.slug}`} className="block h-full">
                    <img
                      src={post.imagemUrl || FALLBACK_IMAGE}
                      alt={post.titulo}
                      className="h-full w-full rounded-t-2xl object-cover md:rounded-l-2xl md:rounded-tr-none"
                    />
                  </Link>
                  <div className="pointer-events-none absolute left-3 top-3">
                    {catHref ? (
                      <Link
                        href={catHref}
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

                <div className="flex flex-col justify-between p-6">
                  <Link href={`/blog/${post.slug}`} className="block">
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
                    href={`/blog/${post.slug}`}
                    className="mt-3 inline-flex w-fit items-center rounded-2xl px-3 py-2 text-sm font-medium text-[#FC1EAD] transition group-hover:bg-[#FC1EAD]/10"
                  >
                    Ler mais
                    <ArrowRightIcon className="ml-1 h-4 w-4" />
                  </Link>
                </div>
              </article>
            )
          })
        )}

        {!loading && !postsError && editorialPosts.length === 0 && (
          <div className="rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
            Nenhum post publicado no momento.
          </div>
        )}
      </div>

      <aside className="space-y-8">
        <div className="rounded-2xl border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Últimos posts</h3>
          <div className="space-y-4">
            {latestPosts.map((post) => (
              <Link
                key={post.id}
                href={`/blog/${post.slug}`}
                className="group flex items-center gap-3 border-b border-gray-200 pb-3 last:border-0"
              >
                <div className="relative h-12 w-12 flex-shrink-0">
                  <img src={post.imagemUrl || FALLBACK_IMAGE} alt={post.titulo} className="h-12 w-12 rounded-md object-cover" />
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
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Categorias</h3>
          {categoriesError ? (
            <ContractState error={categoriesError} onRetry={() => setRetryVersion((value) => value + 1)} compact />
          ) : null}
          <ul className="space-y-3 text-sm">
            {!categoriesError && catalogCategorias.length > 0
              ? catalogCategorias.map((c) => (
                  <li key={c.id} className="flex justify-between border-b border-gray-100 pb-2 last:border-0">
                    <Link
                      href={`/blog/categoria/${encodeURIComponent(c.slug)}`}
                      className="text-gray-700 transition hover:text-[#FC1EAD]"
                    >
                      {c.nome}
                    </Link>
                    <span className="font-semibold text-[#FC1EAD]">{c.postCountPublicados}</span>
                  </li>
                ))
              : !categoriesError ? categoriasFallback.map(([nome, total]) => {
                  const slug = slugifyCategoria(nome === "Sem categoria" ? "" : nome)
                  const href = nome !== "Sem categoria" && slug ? `/blog/categoria/${encodeURIComponent(slug)}` : null
                  return (
                    <li key={nome} className="flex justify-between border-b border-gray-100 pb-2 last:border-0">
                      {href ? (
                        <Link href={href} className="text-gray-700 transition hover:text-[#FC1EAD]">
                          {nome}
                        </Link>
                      ) : (
                        <span className="text-gray-700">{nome}</span>
                      )}
                      <span className="font-semibold text-[#FC1EAD]">{total}</span>
                    </li>
                  )
                }) : null}
            {!categoriesError && catalogCategorias.length === 0 && categoriasFallback.length === 0 && (
              <li className="text-gray-500">Sem categorias publicadas.</li>
            )}
          </ul>
        </div>
      </aside>
    </section>
  )
}
