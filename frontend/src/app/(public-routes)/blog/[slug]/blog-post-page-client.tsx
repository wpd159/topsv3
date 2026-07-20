"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"
import { fetchPublicBlogPost, type BlogPostDetail } from "@/lib/blog-api"
import { SafeBlogPostBody } from "@/lib/blog/safe-blog-body"
import { getPublicLogoUrl } from "@/lib/public-site-assets"
import { ContractState } from '@/components/feedback/contract-state'

const FALLBACK_IMAGE = getPublicLogoUrl()

export default function BlogPostPageClient({
  slug,
  initialPost = null,
}: {
  slug: string
  initialPost?: BlogPostDetail | null
}) {
  const [post, setPost] = useState<BlogPostDetail | null>(initialPost)
  const [loading, setLoading] = useState(!initialPost)
  const [error, setError] = useState<unknown>(null)
  const [retryVersion, setRetryVersion] = useState(0)

  useEffect(() => {
    let active = true

    async function loadPost() {
      setError(null)
      try {
        const data = await fetchPublicBlogPost(slug)
        if (active) {
          setPost(data)
        }
      } catch (loadError) {
        if (active) {
          setError(loadError)
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadPost()

    return () => {
      active = false
    }
  }, [slug, retryVersion])

  if (loading) {
    return (
      <main className="min-h-screen bg-white">
        <section className="px-6 py-10 text-center text-gray-500">
          Carregando post...
        </section>
      </main>
    )
  }

  if (error) {
    return (
      <main className="min-h-screen bg-white px-6 py-10">
        <Link href="/blog" className="mb-8 inline-flex items-center gap-2 text-sm font-medium text-[#FC1EAD]">
          <ArrowLeftIcon className="h-4 w-4" />
          Voltar para o blog
        </Link>
        <ContractState error={error} onRetry={() => setRetryVersion((value) => value + 1)} />
      </main>
    )
  }

  if (!post) {
    return (
      <main className="min-h-screen bg-white">
        <section className="px-6 py-10">
          <Link
            href="/blog"
            className="inline-flex items-center gap-2 text-sm font-medium text-[#FC1EAD] transition hover:opacity-80"
          >
            <ArrowLeftIcon className="h-4 w-4" />
            Voltar para o blog
          </Link>

          <div className="mt-10 rounded-2xl border border-dashed border-gray-200 p-10 text-center text-gray-500">
            Post não encontrado.
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="min-h-screen bg-white">
      <section className="px-6 py-10">
        <Link
          href="/blog"
          className="inline-flex items-center gap-2 text-sm font-medium text-[#FC1EAD] transition hover:opacity-80"
        >
          <ArrowLeftIcon className="h-4 w-4" />
          Voltar para o blog
        </Link>

        <div className="mt-6">
          <span className="inline-flex rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white">
            {post.categoria}
          </span>

          <h1 className="mt-4 text-3xl font-bold leading-tight text-gray-900 md:text-4xl">
            {post.titulo}
          </h1>

          <div className="mt-4 flex items-center gap-4 text-sm text-gray-500">
            <span>{post.autorNome}</span>
            <span>•</span>
            <span>
              {post.publishedAt
                ? new Date(post.publishedAt).toLocaleDateString("pt-BR")
                : new Date(post.updatedAt || post.createdAt || Date.now()).toLocaleDateString("pt-BR")}
            </span>
          </div>
        </div>

        <div className="relative mt-8 h-[260px] w-full overflow-hidden rounded-2xl md:h-[420px]">
          <img
            src={post.imagemUrl || post.ogImageUrl || FALLBACK_IMAGE}
            alt={post.titulo}
            className="h-full w-full object-cover"
          />
        </div>

        <SafeBlogPostBody conteudo={post.conteudo} />
      </section>
    </main>
  )
}
