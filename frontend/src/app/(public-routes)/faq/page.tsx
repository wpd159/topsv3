import type { Metadata } from 'next'
import Link from 'next/link'

import { listarFaqsPublicadas, type FaqPublica } from '@/lib/faq-public-api'
import { buildPublicUrl } from '@/lib/seo/public-url'

export const dynamic = 'force-dynamic'

export const metadata: Metadata = {
  title: 'FAQ - Perguntas frequentes | Tops do Job',
  description: 'Respostas para as principais dúvidas sobre conta, anúncios, pagamentos e segurança no Tops do Job.',
  alternates: {
    canonical: buildPublicUrl('/faq'),
  },
  openGraph: {
    title: 'FAQ - Perguntas frequentes | Tops do Job',
    description: 'Respostas para as principais dúvidas sobre o Tops do Job.',
    url: buildPublicUrl('/faq'),
    type: 'website',
    siteName: 'Tops do Job',
    locale: 'pt_BR',
  },
}

const categorias = [
  ['TODAS', 'Todas'],
  ['CONTA', 'Conta'],
  ['PAGAMENTOS', 'Pagamentos'],
  ['SEGURANCA', 'Segurança'],
  ['ANUNCIOS', 'Anúncios'],
  ['GERAL', 'Geral'],
] as const

type FaqSearchParams = {
  busca?: string
  categoria?: string
}

function normalizarBusca(value: string | undefined) {
  return (value || '').trim().slice(0, 120)
}

function normalizarCategoria(value: string | undefined) {
  const normalized = (value || 'TODAS').trim().toUpperCase()
  return categorias.some(([id]) => id === normalized) ? normalized : 'TODAS'
}

function filtrarFaqs(faqs: FaqPublica[], busca: string, categoria: string) {
  const termo = busca.toLocaleLowerCase('pt-BR')
  return faqs.filter((faq) => {
    const pertenceCategoria = categoria === 'TODAS' || faq.categoria === categoria
    const correspondeBusca = !termo
      || faq.pergunta.toLocaleLowerCase('pt-BR').includes(termo)
      || faq.resposta.toLocaleLowerCase('pt-BR').includes(termo)
    return pertenceCategoria && correspondeBusca
  })
}

export default async function FAQPage({
  searchParams,
}: {
  searchParams: Promise<FaqSearchParams>
}) {
  const params = await searchParams
  const busca = normalizarBusca(params.busca)
  const categoria = normalizarCategoria(params.categoria)
  let indisponivel = false
  let publicadas: FaqPublica[] = []

  try {
    publicadas = await listarFaqsPublicadas()
  } catch {
    indisponivel = true
  }

  const visiveis = filtrarFaqs(publicadas, busca, categoria)
  const grupos = categorias
    .filter(([id]) => id !== 'TODAS')
    .map(([id, rotulo]) => ({
      id,
      rotulo,
      itens: visiveis.filter((faq) => faq.categoria === id),
    }))
    .filter((grupo) => grupo.itens.length > 0)

  const faqJsonLd = visiveis.length > 0
    ? {
        '@context': 'https://schema.org',
        '@type': 'FAQPage',
        mainEntity: visiveis.map((faq) => ({
          '@type': 'Question',
          name: faq.pergunta,
          acceptedAnswer: {
            '@type': 'Answer',
            text: faq.resposta,
          },
        })),
      }
    : null

  return (
    <main className="mx-auto min-h-[70vh] max-w-5xl px-4 py-10 sm:px-6">
      <header className="border-b pb-6">
        <h1 className="text-3xl font-bold text-zinc-950 md:text-4xl">
          FAQ - Perguntas frequentes
        </h1>
        <p className="mt-2 max-w-3xl text-zinc-600">
          Encontre respostas sobre conta, anúncios, pagamentos e segurança.
        </p>
      </header>

      <form
        action="/faq"
        method="get"
        className="grid gap-3 border-b py-5 sm:grid-cols-[minmax(0,1fr)_200px_auto]"
      >
        <label className="sr-only" htmlFor="faq-search">Buscar nas perguntas frequentes</label>
        <input
          id="faq-search"
          name="busca"
          defaultValue={busca}
          maxLength={120}
          placeholder="Busque por palavra-chave"
          className="h-11 min-w-0 border border-zinc-300 bg-white px-3 text-sm outline-none focus:border-pink-500"
        />
        <label className="sr-only" htmlFor="faq-category">Categoria</label>
        <select
          id="faq-category"
          name="categoria"
          defaultValue={categoria}
          className="h-11 border border-zinc-300 bg-white px-3 text-sm outline-none focus:border-pink-500"
        >
          {categorias.map(([id, rotulo]) => (
            <option key={id} value={id}>{rotulo}</option>
          ))}
        </select>
        <button
          type="submit"
          className="h-11 bg-pink-600 px-5 text-sm font-semibold text-white hover:bg-pink-700"
        >
          Buscar
        </button>
      </form>

      {indisponivel ? (
        <div className="my-8 border-y border-amber-200 bg-amber-50 py-5 text-sm text-amber-900">
          As perguntas frequentes estão temporariamente indisponíveis. Tente novamente em instantes.
        </div>
      ) : null}

      {!indisponivel && visiveis.length === 0 ? (
        <div className="my-10 border-y py-10 text-center">
          <p className="font-medium text-zinc-900">Nenhuma pergunta encontrada.</p>
          <p className="mt-1 text-sm text-zinc-500">
            Ajuste a busca ou consulte todas as categorias.
          </p>
        </div>
      ) : null}

      <div className="divide-y">
        {grupos.map((grupo) => (
          <section key={grupo.id} className="py-7" aria-labelledby={`faq-${grupo.id}`}>
            <h2 id={`faq-${grupo.id}`} className="text-xl font-semibold text-zinc-950">
              {grupo.rotulo}
            </h2>
            <div className="mt-3 divide-y border-y">
              {grupo.itens.map((faq) => (
                <details key={faq.id} className="group py-1">
                  <summary className="flex min-h-12 cursor-pointer list-none items-center justify-between gap-4 py-3 font-medium text-zinc-900">
                    <span>{faq.pergunta}</span>
                    <span aria-hidden="true" className="text-pink-600 group-open:rotate-45">+</span>
                  </summary>
                  <p className="whitespace-pre-line pb-5 pr-8 text-sm leading-7 text-zinc-600">
                    {faq.resposta}
                  </p>
                </details>
              ))}
            </div>
          </section>
        ))}
      </div>

      <footer className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t pt-6">
        {(busca || categoria !== 'TODAS') ? (
          <Link href="/faq" className="text-sm font-medium text-pink-600 hover:underline">
            Limpar busca
          </Link>
        ) : <span />}
        <Link
          href="/meus-tickets"
          className="text-sm font-medium text-zinc-700 hover:text-pink-600"
        >
          Ainda com dúvidas? Acesse o suporte
        </Link>
      </footer>

      {faqJsonLd ? (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(faqJsonLd).replace(/</g, '\\u003c'),
          }}
        />
      ) : null}
    </main>
  )
}
