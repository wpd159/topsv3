import type { Metadata } from "next"
import Link from "next/link"
import Hero from "@/components/layout/hero"
import CategoriasSection from "@/components/layout/categoria-section"
import { listarFaqsPublicadas, type FaqPublica } from "@/lib/faq-public-api"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { buildPublicUrl } from "@/lib/seo/public-url"
import { descobrirLocalidadesPublicas } from "@/lib/public-catalog-server-api"
import { isCidadeIndexavelLocal, type LocalIndexingDecision } from "@/lib/seo/local-indexing"

export const metadata: Metadata = {
  title: "Acompanhantes perto de você | Tops do Job",
  description:
    "Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp.",
  alternates: {
    canonical: buildPublicUrl("/"),
  },
  openGraph: {
    title: "Acompanhantes perto de você | Tops do Job",
    description:
      "Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp.",
    url: buildPublicUrl("/"),
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
  other: {
    rating: "adult",
  },
}

interface CidadePopularHome {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  indexacao: LocalIndexingDecision
}

async function buscarCidadesPopularesHome() {
  const descoberta = await descobrirLocalidadesPublicas()
  return descoberta.estados
    .flatMap((estado) =>
      estado.cidades.map((cidade) => ({
        estadoUf: estado.uf,
        cidadeNome: cidade.nome,
        cidadeSlug: cidade.slug,
        totalAnunciosAtivos: cidade.totalAnunciosAtivos,
        indexacao: cidade.indexacao,
      }))
    )
    .sort(
      (a, b) =>
        Number(isCidadeIndexavelLocal(b)) - Number(isCidadeIndexavelLocal(a)) ||
        b.totalAnunciosAtivos - a.totalAnunciosAtivos ||
        a.cidadeNome.localeCompare(b.cidadeNome)
    )
    .slice(0, 12)
}

function HomeCidadesPopulares({ cidades }: { cidades: CidadePopularHome[] }) {
  if (cidades.length === 0) return null

  return (
    <section className="mx-auto mt-10 w-full max-w-7xl px-4">
      <div className="rounded-3xl border border-pink-100 bg-white p-6 shadow-[0_0_22px_rgba(252,30,173,0.10)]">
        <div className="max-w-3xl space-y-2">
          <h2 className="text-2xl font-bold text-gray-900">Acompanhantes em cidades populares</h2>
          <p className="text-sm leading-6 text-gray-600">
            Acesse páginas locais com anúncios de acompanhantes por cidade e continue a busca por
            regiões próximas.
          </p>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
          {cidades.map((cidade) => (
            <Link
              key={`${cidade.estadoUf}-${cidade.cidadeSlug}`}
              href={`/acompanhantes/${cidade.estadoUf.toLowerCase()}/${cidade.cidadeSlug}`}
              className="rounded-2xl border border-pink-200 bg-pink-50 px-4 py-3 text-sm font-semibold text-pink-700 shadow-[0_0_12px_rgba(252,30,173,0.08)] transition hover:-translate-y-0.5 hover:bg-pink-100 hover:shadow-[0_0_18px_rgba(252,30,173,0.16)]"
            >
              {labelAcompanhantesCidade(cidade.cidadeNome)} - {cidade.estadoUf}
            </Link>
          ))}
        </div>
      </div>
    </section>
  )
}

function HomeFaqSection({ faqs }: { faqs: FaqPublica[] }) {
  if (faqs.length === 0) return null

  const faqJsonLd = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: faqs.map((faq) => ({
      "@type": "Question",
      name: faq.pergunta,
      acceptedAnswer: {
        "@type": "Answer",
        text: faq.resposta,
      },
    })),
  }

  return (
    <section
      className="mx-auto mt-12 w-full max-w-7xl px-4 pb-12"
      aria-labelledby="home-faq-title"
    >
      <header className="text-center">
        <p className="text-sm font-semibold uppercase text-pink-600">FAQ</p>
        <h2 id="home-faq-title" className="mt-1 text-3xl font-bold text-gray-900">
          Perguntas Frequentes
        </h2>
      </header>

      <div className="mx-auto mt-8 grid w-full max-w-5xl gap-x-8 lg:grid-cols-2">
        {faqs.map((faq) => (
          <details key={faq.id} className="group min-w-0 border-b border-gray-200 py-1">
            <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 py-4 font-semibold text-gray-900">
              <span className="min-w-0">{faq.pergunta}</span>
              <span
                aria-hidden="true"
                className="shrink-0 text-xl text-pink-600 transition-transform group-open:rotate-45"
              >
                +
              </span>
            </summary>
            <p className="whitespace-pre-line pb-5 pr-8 text-sm leading-7 text-gray-600">
              {faq.resposta}
            </p>
          </details>
        ))}
      </div>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: serializeJsonLd(faqJsonLd),
        }}
      />
    </section>
  )
}

export default async function Home() {
  const [cidades, faqs] = await Promise.all([
    buscarCidadesPopularesHome(),
    listarFaqsPublicadas().catch(() => []),
  ])

  return (
    <>
      <Hero />
      <CategoriasSection />
      <HomeCidadesPopulares cidades={cidades} />
      <HomeFaqSection faqs={faqs} />
    </>
  )
}
