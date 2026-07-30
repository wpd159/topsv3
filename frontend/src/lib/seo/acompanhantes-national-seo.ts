import type {
  PublicCatalogDiscovery,
  PublicCatalogPagination,
} from "@/lib/public-catalog-api"
import type { CidadeNavegacaoPublica } from "@/lib/seo/acompanhantes-navigation"
import { buildPublicUrl } from "@/lib/seo/public-url"

export const ACOMPANHANTES_NATIONAL_TITLE =
  "Acompanhantes em Todo o Brasil por Cidade | Tops do Job"
export const ACOMPANHANTES_NATIONAL_DESCRIPTION =
  "Encontre acompanhantes em todo o Brasil por estado, cidade e bairro. Consulte anúncios ativos e descubra opções disponíveis na sua região."

export const ACOMPANHANTES_NATIONAL_FAQS = [
  {
    pergunta: "Como encontrar acompanhantes na minha cidade?",
    resposta:
      "Escolha um estado e acesse uma cidade com anúncios publicados. Na página local, compare os perfis ativos e os serviços informados em cada anúncio.",
  },
  {
    pergunta: "Posso pesquisar acompanhantes por bairro?",
    resposta:
      "Sim. Quando há cobertura suficiente, as páginas de cidade apresentam bairros com anúncios publicados para refinar a busca.",
  },
  {
    pergunta: "Quais cidades possuem anúncios ativos?",
    resposta:
      "A lista desta página é atualizada com dados do catálogo. Apenas cidades com cobertura suficiente recebem links indexáveis na navegação nacional.",
  },
  {
    pergunta: "Há anúncios para atendimento virtual?",
    resposta:
      "Sim. Um anúncio pode informar atendimento presencial e virtual, ou atendimento exclusivamente virtual, sem criar perfis duplicados.",
  },
  {
    pergunta: "Como denunciar um anúncio?",
    resposta:
      "Abra o detalhe do anúncio e use a ação Denunciar anúncio. A denúncia é enviada para análise administrativa e não revela sua identidade ao anunciante.",
  },
  {
    pergunta: "Como funciona a proteção de conteúdo restrito?",
    resposta:
      "Fotos classificadas como restritas usam uma versão pública protegida. O arquivo original permanece privado e depende da verificação etária para acesso.",
  },
] as const

export type AcompanhantesNationalCoverage = {
  estados: number
  cidades: number
  bairros: number
  anuncios: number
}

function requireCount(value: number, label: string) {
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Contagem pública inválida para ${label}.`)
  }
  return value
}

export function buildAcompanhantesNationalCoverage(
  descoberta: PublicCatalogDiscovery,
  paginacao: Pick<PublicCatalogPagination, "totalItens">,
): AcompanhantesNationalCoverage {
  return {
    estados: requireCount(descoberta.estados.length, "estados"),
    cidades: requireCount(
      descoberta.estados.reduce((total, estado) => total + estado.cidades.length, 0),
      "cidades",
    ),
    bairros: requireCount(
      descoberta.estados.reduce(
        (totalEstados, estado) =>
          totalEstados +
          estado.cidades.reduce(
            (totalCidades, cidade) => totalCidades + cidade.bairros.length,
            0,
          ),
        0,
      ),
      "bairros",
    ),
    anuncios: requireCount(paginacao.totalItens, "anúncios"),
  }
}

export function buildAcompanhantesNationalStructuredData(
  cidadesVisiveis: CidadeNavegacaoPublica[],
) {
  const canonical = buildPublicUrl("/acompanhantes")
  const graph: Array<Record<string, unknown>> = [
    {
      "@type": "CollectionPage",
      "@id": `${canonical}#collection`,
      url: canonical,
      name: ACOMPANHANTES_NATIONAL_TITLE,
      description: ACOMPANHANTES_NATIONAL_DESCRIPTION,
      inLanguage: "pt-BR",
      breadcrumb: { "@id": `${canonical}#breadcrumb` },
      hasPart: [
        ...(cidadesVisiveis.length > 0 ? [{ "@id": `${canonical}#localidades` }] : []),
        { "@id": `${canonical}#perguntas-frequentes` },
      ],
    },
    {
      "@type": "BreadcrumbList",
      "@id": `${canonical}#breadcrumb`,
      itemListElement: [
        {
          "@type": "ListItem",
          position: 1,
          name: "Home",
          item: buildPublicUrl("/"),
        },
        {
          "@type": "ListItem",
          position: 2,
          name: "Acompanhantes",
          item: canonical,
        },
      ],
    },
    {
      "@type": "FAQPage",
      "@id": `${canonical}#perguntas-frequentes`,
      mainEntity: ACOMPANHANTES_NATIONAL_FAQS.map((faq) => ({
        "@type": "Question",
        name: faq.pergunta,
        acceptedAnswer: {
          "@type": "Answer",
          text: faq.resposta,
        },
      })),
    },
  ]

  if (cidadesVisiveis.length > 0) {
    graph.splice(2, 0, {
      "@type": "ItemList",
      "@id": `${canonical}#localidades`,
      name: "Cidades com anúncios publicados",
      numberOfItems: cidadesVisiveis.length,
      itemListElement: cidadesVisiveis.map((cidade, index) => ({
        "@type": "ListItem",
        position: index + 1,
        name: `Acompanhantes em ${cidade.cidadeNome} - ${cidade.estadoUf}`,
        url: buildPublicUrl(
          `/acompanhantes/${cidade.estadoUf.toLowerCase()}/${cidade.cidadeSlug}`,
        ),
      })),
    })
  }

  return {
    "@context": "https://schema.org",
    "@graph": graph,
  }
}
