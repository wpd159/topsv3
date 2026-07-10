import type { Metadata } from "next"
import Link from "next/link"
import Hero from "@/components/layout/hero"
import CategoriasSection from "@/components/layout/categoria-section"
import SegurancaSection from "@/components/layout/seguranca-section"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { corrigirTextoCorrompido } from "@/lib/text/encoding"

export const dynamic = "force-static"
export const revalidate = 3600
export const metadata: Metadata = {
  title: "Acompanhantes perto de você | Tops do Job",
  description:
    "Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp.",
  alternates: {
    canonical: "https://topsdojob.com",
  },
  openGraph: {
    title: "Acompanhantes perto de você | Tops do Job",
    description:
      "Veja anúncios de acompanhantes na sua cidade e região, com contato direto pelo WhatsApp.",
    url: "https://topsdojob.com",
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
}

interface CidadeAtivaHomeDTO {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  shouldIndex?: boolean
}

interface CidadePopularHome {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
}

async function buscarCidadesPopularesHome() {
  try {
    const apiUrl = process.env.NEXT_PUBLIC_API_URL
    if (!apiUrl) return []

    const response = await fetch(`${apiUrl}/anuncios/cidades-ativas`, {
      next: { revalidate: 3600 },
    })

    if (!response.ok) return []

    const data = (await response.json()) as CidadeAtivaHomeDTO[]
    if (!Array.isArray(data)) return []

    return data
      .filter((cidade) => cidade?.estadoUf && cidade?.cidadeNome && cidade?.cidadeSlug)
      .filter((cidade) => cidade.shouldIndex !== false)
      .slice(0, 12)
      .map((cidade) => ({
        estadoUf: String(cidade.estadoUf).toUpperCase(),
        cidadeNome: corrigirTextoCorrompido(String(cidade.cidadeNome)),
        cidadeSlug: String(cidade.cidadeSlug),
      }))
  } catch {
    return []
  }
}

function HomeCidadesPopulares({ cidades }: { cidades: CidadePopularHome[] }) {
  if (cidades.length === 0) return null

  return (
    <section className="mx-auto mt-10 w-full max-w-7xl px-4">
      <div className="rounded-3xl border border-pink-100 bg-white p-6 shadow-sm">
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
              className="rounded-2xl border border-pink-200 bg-pink-50 px-4 py-3 text-sm font-semibold text-pink-700 transition hover:bg-pink-100"
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
      <SegurancaSection />
    </>
  )
}
