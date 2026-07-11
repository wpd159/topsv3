import type { Metadata } from "next"
import Link from "next/link"
import {
  agruparCidadesPorEstado,
  cidadesDaDescobertaPublica,
} from "@/lib/seo/acompanhantes-navigation"
import { descobrirLocalidadesPublicas } from "@/lib/public-catalog-api"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { buildPublicUrl } from "@/lib/seo/public-url"

export const dynamic = "force-dynamic"
export const revalidate = 3600

export const metadata: Metadata = {
  title: "Acompanhantes em sua cidade | Tops do Job",
  description:
    "Encontre acompanhantes na sua cidade e região. Explore páginas locais com anúncios ativos, bairros e contato direto.",
  alternates: {
    canonical: buildPublicUrl("/acompanhantes"),
  },
  openGraph: {
    title: "Acompanhantes em sua cidade | Tops do Job",
    description:
      "Encontre acompanhantes na sua cidade e região. Explore páginas locais com anúncios ativos, bairros e contato direto.",
    url: buildPublicUrl("/acompanhantes"),
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
}

export default async function AcompanhantesIndexPage() {
  const cidades = cidadesDaDescobertaPublica(await descobrirLocalidadesPublicas())
  const estados = agruparCidadesPorEstado(cidades)
  const cidadesPrincipais = cidades.slice(0, 12)

  return (
    <main className="mx-auto w-full px-4 py-10 space-y-10">
      <nav className="mb-2 text-sm text-gray-600">
        <Link href="/" className="hover:text-pink-600">
          Home
        </Link>
        <span className="mx-2">/</span>
        <span className="font-medium text-gray-900">Acompanhantes</span>
      </nav>

      <section className="space-y-4">
        <h1 className="text-4xl font-bold text-gray-900">Encontre acompanhantes na sua cidade</h1>
        <p className="max-w-4xl text-lg text-gray-600">
          Explore anúncios ativos de acompanhantes por cidade, bairros relacionados e navegação
          local. Esta área organiza regiões com cobertura atual na plataforma e facilita o acesso
          às páginas mais próximas de você.
        </p>
      </section>

      {cidadesPrincipais.length > 0 && (
        <section className="space-y-4 rounded-3xl border border-gray-200 bg-white p-6">
          <div className="space-y-2">
            <h2 className="text-2xl font-bold text-gray-900">Cidades principais</h2>
            <p className="text-sm leading-relaxed text-gray-600">
              Acesse diretamente algumas das páginas locais mais relevantes para encontrar
              acompanhantes em cidades com cobertura ativa.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
            {cidadesPrincipais.map((cidade) => (
              <Link
                key={`${cidade.estadoUf}-${cidade.cidadeSlug}`}
                href={`/acompanhantes/${cidade.estadoUf.toLowerCase()}/${cidade.cidadeSlug}`}
                className="rounded-2xl border border-pink-200 bg-pink-50 px-4 py-3 text-sm font-medium text-pink-700 transition hover:bg-pink-100"
              >
                <span className="block">{labelAcompanhantesCidade(cidade.cidadeNome)}</span>
                <span className="mt-1 block text-xs text-pink-600">{cidade.estadoUf}</span>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className="space-y-5">
        <div className="space-y-2">
          <h2 className="text-2xl font-bold text-gray-900">Estados disponíveis</h2>
          <p className="max-w-3xl text-sm leading-relaxed text-gray-600">
            Cada página de estado reúne cidades com anúncios ativos e ajuda a aprofundar a
            navegação para bairros e páginas locais conectadas.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
          {estados.map((estado) => (
            <section
              key={estado.uf}
              className="rounded-3xl border border-gray-200 bg-white p-6 shadow-sm"
            >
              <div className="flex flex-col gap-2 border-b border-gray-100 pb-4 sm:flex-row sm:items-end sm:justify-between">
                <div>
                  <h3 className="text-xl font-bold text-gray-900">
                    <Link
                      href={`/acompanhantes/${estado.uf.toLowerCase()}`}
                      className="hover:text-pink-600"
                    >
                      {estado.nome}
                    </Link>
                  </h3>
                  <p className="text-sm text-gray-500">{estado.uf}</p>
                </div>
                <Link
                  href={`/acompanhantes/${estado.uf.toLowerCase()}`}
                  className="text-sm font-medium text-pink-600 hover:text-pink-700"
                >
                  Ver estado completo
                </Link>
              </div>

              <div className="mt-5 space-y-3">
                <p className="text-sm leading-relaxed text-gray-600">
                  Navegue por cidades de {estado.nome} com páginas locais conectadas entre si
                  para ampliar a descoberta de perfis, bairros e anúncios ativos.
                </p>

                <div className="grid grid-cols-2 gap-3">
                  {estado.cidades.slice(0, 8).map((cidade) => (
                    <Link
                      key={cidade.cidadeSlug}
                      href={`/acompanhantes/${estado.uf.toLowerCase()}/${cidade.cidadeSlug}`}
                      className="rounded-xl border border-gray-200 px-4 py-3 text-sm font-medium text-gray-700 transition hover:border-pink-200 hover:text-pink-600"
                    >
                      {labelAcompanhantesCidade(cidade.cidadeNome)}
                    </Link>
                  ))}
                </div>
              </div>
            </section>
          ))}
        </div>
      </section>
    </main>
  )
}
