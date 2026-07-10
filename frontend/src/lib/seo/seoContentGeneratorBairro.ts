import {
  gerarSeoProgramaticoLocal,
  type SeoFaqItem,
} from "@/lib/seo/programmatic-content"
import {
  labelAcompanhantesBairro,
  labelAcompanhantesBairroCidade,
  labelAcompanhantesCidade,
} from "@/lib/seo/local-labels"

interface SeoContentBairroParams {
  bairroNome: string
  cidadeNome: string
  estadoNome: string
  estadoUf: string
  bairros?: string[]
}

export interface SeoContentBairro {
  title: string
  h1: string
  metaDescription: string
  resumoTopo: string
  intro: string[]
  comparacao: string[]
  singular: string[]
  content: string
  faq: SeoFaqItem[]
}

export function gerarConteudoSeoBairro(params: SeoContentBairroParams): SeoContentBairro {
  const seo = gerarSeoProgramaticoLocal({
    tipo: "bairro",
    bairroNome: params.bairroNome,
    cidadeNome: params.cidadeNome,
    estadoNome: params.estadoNome,
    estadoUf: params.estadoUf,
    bairros: params.bairros,
  })

  return {
    title: `${labelAcompanhantesBairroCidade(params.bairroNome, params.cidadeNome)} - ${params.estadoUf} | Tops do Job`,
    h1: `${labelAcompanhantesBairroCidade(params.bairroNome, params.cidadeNome)} - ${params.estadoUf}`,
    metaDescription: seo.description,
    resumoTopo: seo.resumoTopo,
    intro: seo.intro,
    comparacao: seo.comoUsar,
    singular: seo.singular,
    content: "",
    faq: seo.faq,
  }
}

export function gerarBreadcrumbSchemaBairro(
  baseUrl: string,
  bairroNome: string,
  cidadeNome: string,
  estadoUf: string,
  cidadeSlug: string,
  bairroSlug: string
) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [
      {
        "@type": "ListItem",
        position: 1,
        name: "Home",
        item: baseUrl,
      },
      {
        "@type": "ListItem",
        position: 2,
        name: "Acompanhantes",
        item: `${baseUrl}/acompanhantes`,
      },
      {
        "@type": "ListItem",
        position: 3,
        name: estadoUf,
        item: `${baseUrl}/acompanhantes/${estadoUf.toLowerCase()}`,
      },
      {
        "@type": "ListItem",
        position: 4,
        name: labelAcompanhantesCidade(cidadeNome),
        item: `${baseUrl}/acompanhantes/${estadoUf.toLowerCase()}/${cidadeSlug}`,
      },
      {
        "@type": "ListItem",
        position: 5,
        name: labelAcompanhantesBairro(bairroNome),
        item: `${baseUrl}/acompanhantes/${estadoUf.toLowerCase()}/${cidadeSlug}/${bairroSlug}`,
      },
    ],
  }
}
