"use client"

import Link from "next/link"
import { ArrowLeftIcon } from "@heroicons/react/24/solid"
import type { ProgrammaticBlogPublic } from "@/lib/programmatic-blog-api"

function siteBase() {
  return (process.env.NEXT_PUBLIC_SITE_URL || "https://topsdojob.com").replace(/\/$/, "")
}

export default function ProgrammaticBlogPageClient({
  data,
}: {
  data: ProgrammaticBlogPublic
}) {
  const base = siteBase()

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

        <nav className="mt-4 text-sm text-gray-500" aria-label="Breadcrumb">
          <ol className="flex flex-wrap items-center gap-2">
            <li>
              <Link href="/" className="hover:text-[#FC1EAD]">
                Início
              </Link>
            </li>
            <li aria-hidden>/</li>
            <li>
              <Link href="/blog" className="hover:text-[#FC1EAD]">
                Blog
              </Link>
            </li>
            <li aria-hidden>/</li>
            <li className="text-gray-800">{data.h1}</li>
          </ol>
        </nav>

        <div className="mt-6">
          <span className="inline-flex rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-800">
            Guia local · {data.tema.replace(/-/g, " ")}
          </span>

          <h1 className="mt-4 text-3xl font-bold leading-tight text-gray-900 md:text-4xl">{data.h1}</h1>

          <p className="mt-2 text-sm text-gray-500">
            {data.cidadeNome} · {data.estadoNome} ({data.estadoUf})
          </p>
        </div>

        {/* Interlinks (cidades, temas, blog) já vêm no HTML gerado para evitar duplicação e doorway */}
        <article
          className="prose prose-gray prose-headings:font-bold prose-h2:mt-10 prose-h2:text-2xl prose-h3:mt-6 prose-h3:text-xl prose-a:text-[#FC1EAD] mt-8 max-w-none"
          dangerouslySetInnerHTML={{ __html: data.contentHtml }}
        />

        <p className="mt-10 text-xs text-gray-400">
          Conteúdo informativo. Atualizado em:{" "}
          {data.updatedAtIso
            ? new Date(data.updatedAtIso).toLocaleString("pt-BR")
            : "—"}{" "}
          ·{" "}
          <a href={`${base}${data.canonicalPath}`} className="underline">
            URL canônica
          </a>
        </p>
      </section>
    </main>
  )
}
