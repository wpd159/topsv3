import {
  gerarSeoProgramaticoLocal,
  type SeoFaqItem,
} from "@/lib/seo/programmatic-content"
import { labelAcompanhantesCidade } from "@/lib/seo/local-labels"
import { isCidadeIndexavelLocal } from "@/lib/seo/local-indexing"
import { buildPublicUrl } from "@/lib/seo/public-url"
import type { PublicLocalIndexingDecision } from "@/lib/public-catalog-api"

export interface CidadeSeoCategoria {
  codigo: string
  nome: string
  quantidadeAnuncios: number
}

export interface CidadeSeoBairro {
  bairroId?: number
  bairroNome: string
  bairroSlug: string
  quantidadeAnuncios?: number
  indexacao: PublicLocalIndexingDecision
}

export interface CidadeSeoCidadeRelacionada {
  estadoUf: string
  cidadeNome: string
  cidadeSlug: string
  totalAnunciosAtivos?: number
  indexacao: PublicLocalIndexingDecision
}

export interface CidadeSeoAggregate {
  estadoUf: string
  estadoNome: string
  cidadeNome: string
  cidadeSlug: string
  totalAnunciosAtivos: number
  totalBairrosAtivos: number
  totalCategoriasAtivas: number
  quantidadeAnunciosDestaque?: number
  quantidadeAnunciosRecentes?: number
  ultimaAtualizacao?: string | null
  indexacao: PublicLocalIndexingDecision
  robots?: string | null
  reasonCodes?: string[]
  bairros: CidadeSeoBairro[]
  categoriasPrincipais: CidadeSeoCategoria[]
  cidadesRelacionadas: CidadeSeoCidadeRelacionada[]
  possuiStories?: boolean
  possuiConteudoRestrito?: boolean
  possuiDestaques?: boolean
  possuiRecentes?: boolean
}

export interface CidadeSeoFaqItem extends SeoFaqItem {}

export interface CidadeSeoEditorial {
  modo: "completo" | "reduzido"
  intro: string[]
  bairros: string[]
  comoUsar: string[]
  navegacao: string[]
  singular: string[]
  faq: CidadeSeoFaqItem[]
}

export interface CidadeSeoEditorialProvider {
  gerarBlocos(input: CidadeSeoAggregate): Promise<CidadeSeoEditorial> | CidadeSeoEditorial
}

interface AnuncioSeoItem {
  slug: string
  titulo: string
  fotosUrl?: string[]
}

const SEO_IMAGE_FALLBACK = buildPublicUrl("/2151117281.jpg")

function isComplianceOrBackendImage(url?: string | null) {
  if (!url) return true

  try {
    const parsed = new URL(url)
    return (
      parsed.hostname.includes("backend.topsdojob.com") ||
      parsed.pathname.includes("/compliance/assets/")
    )
  } catch {
    return true
  }
}

function resolveSeoImage(url?: string | null) {
  return isComplianceOrBackendImage(url) ? SEO_IMAGE_FALLBACK : url
}

function hashString(value: string) {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  }
  return hash
}

function formatarLista(values: string[]) {
  if (values.length === 0) return ""
  if (values.length === 1) return values[0]
  if (values.length === 2) return `${values[0]} e ${values[1]}`
  return `${values.slice(0, -1).join(", ")} e ${values[values.length - 1]}`
}

function formatarDataAtualizacao(value?: string | null) {
  if (!value) return "Atualização recente"
  const data = new Date(value)
  if (Number.isNaN(data.getTime())) return "Atualização recente"

  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(data)
}

function criarConteudoFallback(dados: CidadeSeoAggregate): CidadeSeoEditorial {
  const indexavel = isCidadeIndexavelLocal(dados)
  const bairrosPrincipais = dados.bairros.slice(0, 5).map((bairro) => bairro.bairroNome)
  const seo = gerarSeoProgramaticoLocal({
    tipo: "cidade",
    cidadeNome: dados.cidadeNome,
    estadoNome: dados.estadoNome,
    estadoUf: dados.estadoUf,
    bairros: bairrosPrincipais,
    totalAnuncios: dados.totalAnunciosAtivos,
    totalBairros: dados.totalBairrosAtivos,
    totalRecentes: dados.quantidadeAnunciosRecentes,
    totalDestaques: dados.quantidadeAnunciosDestaque,
  })
  const bairrosEditorial =
    bairrosPrincipais.length > 0
      ? [
          `Para explorar acompanhantes por bairro em ${dados.cidadeNome}, use os links locais para comparar ${formatarLista(bairrosPrincipais)} e outras regiões com anúncios ativos.`,
        ]
      : [seo.paragrafos[1]].filter(Boolean)

  return {
    modo: indexavel ? "completo" : "reduzido",
    intro: seo.intro,
    bairros: bairrosEditorial,
    comoUsar: seo.comoUsar,
    navegacao: seo.perfilRegiao,
    singular: seo.singular,
    faq: indexavel ? seo.faq : seo.faq.slice(0, 2),
  }
}

export async function gerarConteudoProgramaticoCidade(
  dados: CidadeSeoAggregate,
  provider?: CidadeSeoEditorialProvider
) {
  const fallback = criarConteudoFallback(dados)

  if (!provider) {
    return fallback
  }

  try {
    const resultado = await provider.gerarBlocos(dados)
    return {
      modo: resultado.modo || fallback.modo,
      intro: resultado.intro?.length ? resultado.intro : fallback.intro,
      bairros: resultado.bairros?.length ? resultado.bairros : fallback.bairros,
      comoUsar: resultado.comoUsar?.length ? resultado.comoUsar : fallback.comoUsar,
      navegacao: resultado.navegacao?.length ? resultado.navegacao : fallback.navegacao,
      singular: resultado.singular?.length ? resultado.singular : fallback.singular,
      faq: resultado.faq?.length ? resultado.faq : fallback.faq,
    }
  } catch {
    return fallback
  }
}

function gerarSeoCidade(dados: CidadeSeoAggregate) {
  return gerarSeoProgramaticoLocal({
    tipo: "cidade",
    cidadeNome: dados.cidadeNome,
    estadoNome: dados.estadoNome,
    estadoUf: dados.estadoUf,
    bairros: dados.bairros.slice(0, 5).map((bairro) => bairro.bairroNome),
    totalAnuncios: dados.totalAnunciosAtivos,
    totalBairros: dados.totalBairrosAtivos,
    totalRecentes: dados.quantidadeAnunciosRecentes,
    totalDestaques: dados.quantidadeAnunciosDestaque,
  })
}

export function gerarTituloMetadataCidade(dados: CidadeSeoAggregate, page: number) {
  const title = `${labelAcompanhantesCidade(dados.cidadeNome)}, ${dados.estadoUf} | Tops do Job`
  return page > 0 ? `${title} | Página ${page + 1}` : title
}

export function gerarDescricaoMetadataCidade(dados: CidadeSeoAggregate, page: number) {
  const seo = gerarSeoCidade(dados)
  return page > 0 ? `${seo.description} Página ${page + 1}.` : seo.description
}

export function gerarDescricaoTopoCidade(dados: CidadeSeoAggregate) {
  const cidadeNome = dados.cidadeNome
  const estadoUf = dados.estadoUf
  const bairros = dados.bairros
    .slice(0, 3)
    .map((bairro) => bairro.bairroNome)
    .filter(Boolean)
  const seed = hashString(`${cidadeNome}:${estadoUf}`)
  const bairrosTexto = bairros.length > 0 ? `, incluindo buscas por ${formatarLista(bairros)}` : ""

  const variacoes = [
    `Encontre acompanhantes em ${cidadeNome} - ${estadoUf} com fotos nos perfis, contato direto e anúncios organizados por contexto local${bairrosTexto}.`,
    `Veja perfis ativos em ${cidadeNome} - ${estadoUf}, compare fotos, informações do anúncio e caminhos por bairros relacionados${bairrosTexto}.`,
    `A página de ${cidadeNome} - ${estadoUf} reúne anúncios com navegação local, contato direto e links para continuar a busca por regiões próximas${bairrosTexto}.`,
  ]

  return variacoes[seed % variacoes.length]
}

export function gerarBreadcrumbSchemaCidade(baseUrl: string, dados: CidadeSeoAggregate) {
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
        name: dados.estadoUf,
        item: `${baseUrl}/acompanhantes/${dados.estadoUf.toLowerCase()}`,
      },
      {
        "@type": "ListItem",
        position: 4,
        name: labelAcompanhantesCidade(dados.cidadeNome),
        item: `${baseUrl}/acompanhantes/${dados.estadoUf.toLowerCase()}/${dados.cidadeSlug}`,
      },
    ],
  }
}

export function gerarItemListSchemaCidade(baseUrl: string, anuncios: AnuncioSeoItem[]) {
  if (!anuncios.length) return null

  return {
    "@context": "https://schema.org",
    "@type": "ItemList",
    numberOfItems: anuncios.length,
    itemListElement: anuncios.map((anuncio, index) => ({
      "@type": "ListItem",
      position: index + 1,
      name: anuncio.titulo,
      url: `${baseUrl}/anuncios/${anuncio.slug}`,
      image: resolveSeoImage(anuncio.fotosUrl?.[0]),
    })),
  }
}

export function formatarResumoAtualizacao(dados: CidadeSeoAggregate) {
  return formatarDataAtualizacao(dados.ultimaAtualizacao)
}
