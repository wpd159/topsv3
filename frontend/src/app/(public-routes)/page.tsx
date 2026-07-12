import type { Metadata } from "next"
import Link from "next/link"
import Hero from "@/components/layout/hero"
import CategoriasSection from "@/components/layout/categoria-section"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { buildPublicUrl } from "@/lib/seo/public-url"
import { descobrirLocalidadesPublicas } from "@/lib/public-catalog-api"

export const dynamic = "force-dynamic"
export const revalidate = 3600
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
}

interface CidadePopularHome {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
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
      }))
    )
    .sort((a, b) => b.totalAnunciosAtivos - a.totalAnunciosAtivos || a.cidadeNome.localeCompare(b.cidadeNome))
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

export default async function Home() {
  const cidades = await buscarCidadesPopularesHome()

  return (
    <>
      <Hero />
      <CategoriasSection />
      <HomeCidadesPopulares cidades={cidades} />
    </>
  )
}
