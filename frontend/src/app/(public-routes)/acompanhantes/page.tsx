import type { Metadata } from "next"
import Link from "next/link"
import {
  agruparCidadesPorEstado,
  cidadesDaDescobertaPublica,
} from "@/lib/seo/acompanhantes-navigation"
import {
  descobrirLocalidadesPublicas,
  listarAnunciosPublicos,
} from "@/lib/public-catalog-api"
import { serializeJsonLd } from "@/lib/seo/json-ld"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { buildPublicUrl } from "@/lib/seo/public-url"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import {
  ACOMPANHANTES_NATIONAL_DESCRIPTION,
  ACOMPANHANTES_NATIONAL_FAQS,
  ACOMPANHANTES_NATIONAL_TITLE,
  buildAcompanhantesNationalCoverage,
  buildAcompanhantesNationalStructuredData,
} from "@/lib/seo/acompanhantes-national-seo"

export const dynamic = "force-dynamic"
export const revalidate = 3600

export const metadata: Metadata = {
  title: ACOMPANHANTES_NATIONAL_TITLE,
  description: ACOMPANHANTES_NATIONAL_DESCRIPTION,
  alternates: {
    canonical: buildPublicUrl("/acompanhantes"),
  },
  openGraph: {
    title: ACOMPANHANTES_NATIONAL_TITLE,
    description: ACOMPANHANTES_NATIONAL_DESCRIPTION,
    url: buildPublicUrl("/acompanhantes"),
    type: "website",
    siteName: "Tops do Job",
    locale: "pt_BR",
  },
  twitter: {
    card: "summary",
    title: ACOMPANHANTES_NATIONAL_TITLE,
    description: ACOMPANHANTES_NATIONAL_DESCRIPTION,
  },
}

export default async function AcompanhantesIndexPage() {
  const [descoberta, anunciosPublicados] = await Promise.all([
    descobrirLocalidadesPublicas(),
    listarAnunciosPublicos("TODOS", "", 0, 1),
  ])
  const cobertura = buildAcompanhantesNationalCoverage(
    descoberta,
    anunciosPublicados.paginacao,
  )
  const cidadesIndexaveis = cidadesDaDescobertaPublica(descoberta)
    .filter((cidade) => isCidadeIndexavelLocal(cidade))
    .sort(
      (a, b) =>
        (b.totalAnunciosAtivos ?? 0) - (a.totalAnunciosAtivos ?? 0) ||
        a.cidadeNome.localeCompare(b.cidadeNome, "pt-BR"),
    )
  const estados = agruparCidadesPorEstado(cidadesIndexaveis)
  const cidadesPrincipais = cidadesIndexaveis.slice(0, 12)
  const structuredData = buildAcompanhantesNationalStructuredData(cidadesPrincipais)

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
        <h1 className="text-4xl font-bold text-gray-900">
          Acompanhantes - Cidades do Brasil
        </h1>
        <p className="max-w-4xl text-lg text-gray-600">
          {ACOMPANHANTES_NATIONAL_DESCRIPTION}
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
                <span className="block">
                  {labelAcompanhantesCidade(cidade.cidadeNome)} - {cidade.estadoUf}
                </span>
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
                      {labelAcompanhantesCidade(cidade.cidadeNome)} - {estado.uf}
                    </Link>
                  ))}
                </div>
              </div>
            </section>
          ))}
        </div>
      </section>

      <section
        aria-labelledby="cobertura-nacional"
        className="space-y-4 border-t border-gray-200 pt-8"
      >
        <h2 id="cobertura-nacional" className="text-2xl font-bold text-gray-900">
          Cobertura nacional atual
        </h2>
        <p className="max-w-4xl leading-relaxed text-gray-700">
          O Tops do Job possui anúncios publicados em {cobertura.estados} estados,{" "}
          {cobertura.cidades} cidades e {cobertura.bairros} bairros do Brasil. O catálogo
          reúne {cobertura.anuncios} anúncios publicados elegíveis neste momento.
        </p>
      </section>

      <section
        aria-labelledby="como-encontrar"
        className="space-y-4 border-t border-gray-200 pt-8"
      >
        <h2 id="como-encontrar" className="text-2xl font-bold text-gray-900">
          Como encontrar acompanhantes por cidade
        </h2>
        <p className="max-w-4xl leading-relaxed text-gray-700">
          Comece pelo estado, escolha uma cidade e, quando houver cobertura suficiente,
          refine a navegação pelo bairro. Compare apenas anúncios ativos, confira os serviços
          descritos e observe se o atendimento informado é presencial, virtual ou
          exclusivamente virtual. Um mesmo anúncio pode oferecer atendimento presencial e
          virtual sem duplicar o perfil ou a URL canônica.
        </p>
      </section>

      <section
        aria-labelledby="seguranca-verificacao"
        className="space-y-4 border-t border-gray-200 pt-8"
      >
        <h2 id="seguranca-verificacao" className="text-2xl font-bold text-gray-900">
          Segurança e verificação no Tops do Job
        </h2>
        <p className="max-w-4xl leading-relaxed text-gray-700">
          A plataforma oferece fluxos de verificação documental das anunciantes, moderação
          dos dados dos anúncios e classificação individual das fotos. Conteúdo restrito usa
          proteção específica e confirmação etária, e anúncios podem ser denunciados para
          análise administrativa. O contato ocorre diretamente entre visitante e anunciante:
          o Tops do Job oferece espaço publicitário e não intermedeia o atendimento.
        </p>
        <nav
          aria-label="Informações de segurança e suporte"
          className="flex flex-wrap gap-x-5 gap-y-3 text-sm"
        >
          <Link
            href="/aviso-seguranca-whatsapp"
            className="font-medium text-pink-600 hover:text-pink-700"
          >
            Segurança no WhatsApp
          </Link>
          <Link
            href="/termos-de-uso"
            className="font-medium text-pink-600 hover:text-pink-700"
          >
            Termos de uso
          </Link>
          <Link
            href="/anuncios"
            className="font-medium text-pink-600 hover:text-pink-700"
          >
            Anúncios e denúncias
          </Link>
          <Link href="/blog" className="font-medium text-pink-600 hover:text-pink-700">
            Blog
          </Link>
          <Link href="/faq" className="font-medium text-pink-600 hover:text-pink-700">
            FAQs
          </Link>
        </nav>
      </section>

      <section
        aria-labelledby="perguntas-frequentes"
        className="space-y-5 border-t border-gray-200 pt-8"
      >
        <h2 id="perguntas-frequentes" className="text-2xl font-bold text-gray-900">
          Perguntas frequentes
        </h2>
        <div className="divide-y divide-gray-200 border-y border-gray-200">
          {ACOMPANHANTES_NATIONAL_FAQS.map((faq) => (
            <details key={faq.pergunta} className="group py-4">
              <summary className="cursor-pointer font-semibold text-gray-900">
                {faq.pergunta}
              </summary>
              <p className="max-w-4xl pt-3 leading-relaxed text-gray-700">{faq.resposta}</p>
            </details>
          ))}
        </div>
      </section>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: serializeJsonLd(structuredData),
        }}
      />
    </main>
  )
}
