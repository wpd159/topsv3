import Link from "next/link"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"
import type { BlogPostDetail } from "@/lib/blog-api"
import { SafeBlogPostBody } from "@/lib/blog/safe-blog-body"
import { getPublicLogoUrl } from "@/lib/public-site-assets"

const FALLBACK_IMAGE = getPublicLogoUrl()

export default function BlogPostPageClient({ post }: { post: BlogPostDetail }) {
  return (
    <main className="min-h-screen bg-white">
      <article className="mx-auto max-w-5xl px-6 py-10">
        <Link
          href="/blog"
          className="inline-flex items-center gap-2 text-sm font-medium text-[#FC1EAD] transition hover:opacity-80"
        >
          <ArrowLeftIcon className="h-4 w-4" />
          Voltar para o blog
        </Link>

        <header className="mt-6">
          <Link
            href={`/blog/categoria/${encodeURIComponent(post.categoriaSlug)}`}
            className="inline-flex rounded-full bg-[#FC1EAD] px-3 py-1 text-xs font-semibold text-white"
          >
            {post.categoria}
          </Link>

          <h1 className="mt-4 text-3xl font-bold leading-tight text-gray-900 md:text-4xl">
            {post.titulo}
          </h1>

          <div className="mt-4 flex flex-wrap items-center gap-2 text-sm text-gray-500">
            <span>{post.autorNome}</span>
            <span aria-hidden="true">•</span>
            <time dateTime={post.publishedAt || undefined}>
              {new Date(post.publishedAt || post.updatedAt || post.createdAt || 0)
                .toLocaleDateString("pt-BR")}
            </time>
          </div>
        </header>

        <div className="relative mt-8 aspect-[16/9] w-full overflow-hidden rounded-lg">
          <img
            src={post.imagemUrl || post.ogImageUrl || FALLBACK_IMAGE}
            alt={post.titulo}
            className="h-full w-full object-cover"
          />
        </div>

        <p className="mt-8 text-lg leading-8 text-gray-700">{post.resumo}</p>
        <SafeBlogPostBody conteudo={post.conteudo} />
      </article>
    </main>
  )
}
