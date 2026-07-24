import { corrigirEstruturaTexto } from '@/lib/text/encoding'
import type { MidiaPublica } from '@/lib/media/public-media'
import {
  parseVisualizacoesCanonicas,
  type VisualizacoesCanonicas,
} from '@/lib/visualizacoes-canonicas'
import {
  ApiContractError,
  apiErrorFromResponse,
  publicApiUrl,
  requireArrayPayload,
} from '@/lib/api-contract'

export class PublicCatalogApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'PublicCatalogApiError'
    this.status = status
  }
}

export function isPublicCatalogNotFound(error: unknown) {
  return (
    (error instanceof PublicCatalogApiError || error instanceof ApiContractError) &&
    (error.status === 400 || error.status === 404)
  )
}

export type PublicCatalogLocation = {
  uf: string
  estado: string
  cidade: string | null
  cidadeSlug: string | null
  bairro: string | null
  bairroSlug: string | null
  enderecoResumido: string | null
}

export type PublicCatalogCard = {
  id: string
  slug: string
  titulo: string
  descricao?: string | null
  preco?: number | null
  categoria?: string | null
  idade?: number | null
  midias: MidiaPublica[]
  estadoUf: string
  estadoNome: string
  cidadeNome?: string | null
  cidadeSlug?: string | null
  bairroNome?: string | null
  bairroSlug?: string | null
  enderecoResumido?: string | null
  destaqueAtivo: boolean
  topoAtivo: boolean
  carrosselDisponivel: boolean
  videoHabilitado: boolean
  whatsappCardEnabled: boolean
  comLocal: boolean
  fazAnal: boolean
  locaisAtendimento: string[]
  servicos: string[]
  beneficiosPublicos: string[]
  anunciaDesde?: string | null
  publicadoEm?: string | null
  visualizacoes: VisualizacoesCanonicas
}

export type PublicCatalogPagination = {
  pagina: number
  tamanho: number
  totalItens: number
  totalPaginas: number
  ordemSeed: string
}

export type PublicCatalogList = {
  itens: PublicCatalogCard[]
  paginacao: PublicCatalogPagination
  localidade: PublicCatalogLocation
  seo: Record<string, unknown>
}

export type PublicCatalogNeighborhood = {
  nome: string
  slug: string
  totalAnunciosAtivos: number
  ultimaAtualizacao?: string | null
}

export type PublicCatalogCity = {
  nome: string
  slug: string
  totalAnunciosAtivos: number
  ultimaAtualizacao?: string | null
  bairros: PublicCatalogNeighborhood[]
}

export type PublicCatalogState = {
  uf: string
  nome: string
  totalAnunciosAtivos: number
  ultimaAtualizacao?: string | null
  cidades: PublicCatalogCity[]
}

export type PublicCatalogDiscovery = {
  estados: PublicCatalogState[]
}

export type PublicSitemapEntry = {
  slug: string
  estadoUf: string
  cidadeSlug: string
  bairroSlug?: string | null
  atualizadoEm?: string | null
  publico: true
  indexavel: true
}

export type PublicCatalogCityAggregate = {
  estadoUf: string
  estadoNome: string
  cidadeNome: string
  cidadeSlug: string
  totalAnunciosAtivos: number
  ultimaAtualizacao?: string | null
  bairros: Array<{
    bairroNome: string
    bairroSlug: string
    quantidadeAnuncios: number
    ultimaAtualizacao?: string | null
  }>
  categoriasPrincipais: Array<{
    codigo: string
    nome: string
    quantidadeAnuncios: number
  }>
  cidadesRelacionadas: Array<{
    estadoUf: string
    cidadeNome: string
    cidadeSlug: string
    totalAnunciosAtivos: number
    ultimaAtualizacao?: string | null
  }>
}

export type PublicCatalogDetail = PublicCatalogCard & {
  descricao: string | null
  username?: string | null
  idade?: number | null
  idadeOculta: boolean
  contatoPublico?: string | null
  pendenciaContatoPublico?: string | null
  indexavelSeo: boolean
}

export type PublicHomeCategory = {
  id: string
  identificador: string
  titulo: string
  descricao: string
  destino: string
  imagemPublicaUrl: string
  ordem: number
  ativo: true
}

export type PublicCategoryList = {
  itens: PublicCatalogCard[]
  paginacao: PublicCatalogPagination
  categoria: string | null
}

type RawLocation = {
  uf: string
  estado: string
  cidade?: string | null
  cidadeSlug?: string | null
  bairro?: string | null
  bairroSlug?: string | null
  enderecoResumido?: string | null
}

type RawCard = {
  id: string
  slug: string
  titulo: string
  descricaoResumo?: string | null
  descricao?: string | null
  preco?: number | null
  categoria?: string | null
  localizacao: RawLocation
  midias?: MidiaPublica[]
  destaque?: boolean
  topo?: boolean
  midiaExtra?: boolean
  story?: boolean
  contatoDisponivel?: boolean
  whatsappCard?: boolean
  comLocal?: boolean
  fazAnal?: boolean
  locaisAtendimento?: string[]
  servicos?: string[]
  beneficiosPublicos?: string[]
  anunciaDesde?: string | null
  publicadoEm?: string | null
  contatoPublico?: string | null
  pendenciaContatoPublico?: string | null
  username?: string | null
  idade?: number | null
  idadeOculta?: boolean
  seo?: {
    indexavelFuturo?: boolean
  }
  visualizacoes?: unknown
}

type RawList = {
  itens: RawCard[]
  paginacao: PublicCatalogPagination
  localidade: RawLocation
  seo: Record<string, unknown>
}

type RawCategoryList = {
  itens: RawCard[]
  paginacao: PublicCatalogPagination
  categoria?: string | null
}

type RawCityAggregate = {
  estadoUf: string
  estadoNome: string
  cidadeNome: string
  cidadeSlug: string
  totalAnunciosAtivos: number
  ultimaAtualizacao?: string | null
  bairros: Array<{
    nome: string
    slug: string
    totalAnunciosAtivos: number
    ultimaAtualizacao?: string | null
  }>
  categorias: Array<{
    codigo: string
    nome: string
    totalAnunciosAtivos: number
  }>
  cidadesRelacionadas: PublicCatalogCity[]
}

async function requestJson<T>(path: string, init: RequestInit = {}) {
  const response = await fetch(publicApiUrl(path), {
    ...init,
    credentials: 'include',
  })
  if (!response.ok) {
    throw await apiErrorFromResponse(response)
  }
  return corrigirEstruturaTexto(await response.json()) as T
}

function mapLocation(raw: RawLocation): PublicCatalogLocation {
  return {
    uf: raw.uf,
    estado: raw.estado,
    cidade: raw.cidade ?? null,
    cidadeSlug: raw.cidadeSlug ?? null,
    bairro: raw.bairro ?? null,
    bairroSlug: raw.bairroSlug ?? null,
    enderecoResumido: raw.enderecoResumido ?? null,
  }
}

function mapCard(raw: RawCard): PublicCatalogCard {
  const localizacao = mapLocation(raw.localizacao)
  const midias = Array.isArray(raw.midias) ? raw.midias : []
  return {
    id: raw.id,
    slug: raw.slug,
    titulo: raw.titulo,
    descricao: raw.descricao ?? raw.descricaoResumo ?? null,
    preco: raw.preco ?? null,
    categoria: raw.categoria ?? null,
    idade: raw.idade ?? null,
    midias,
    estadoUf: localizacao.uf,
    estadoNome: localizacao.estado,
    cidadeNome: localizacao.cidade,
    cidadeSlug: localizacao.cidadeSlug,
    bairroNome: localizacao.bairro,
    bairroSlug: localizacao.bairroSlug,
    enderecoResumido: localizacao.enderecoResumido,
    destaqueAtivo: Boolean(raw.destaque),
    topoAtivo: Boolean(raw.topo),
    carrosselDisponivel: Boolean(raw.midiaExtra),
    videoHabilitado: midias.some((midia) => midia.tipo === 'VIDEO'),
    whatsappCardEnabled: Boolean(raw.whatsappCard) && Boolean(raw.contatoDisponivel),
    comLocal: Boolean(raw.comLocal),
    fazAnal: Boolean(raw.fazAnal),
    locaisAtendimento: Array.isArray(raw.locaisAtendimento) ? raw.locaisAtendimento : [],
    servicos: Array.isArray(raw.servicos) ? raw.servicos : [],
    beneficiosPublicos: Array.isArray(raw.beneficiosPublicos) ? raw.beneficiosPublicos : [],
    anunciaDesde: raw.anunciaDesde ?? null,
    publicadoEm: raw.publicadoEm ?? null,
    visualizacoes: parseVisualizacoesCanonicas(raw.visualizacoes),
  }
}

function mapList(raw: RawList): PublicCatalogList {
  return {
    itens: raw.itens.map(mapCard),
    paginacao: raw.paginacao,
    localidade: mapLocation(raw.localidade),
    seo: raw.seo,
  }
}

function listQuery(pagina: number, tamanho: number, ordemSeed?: string) {
  const query = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  if (ordemSeed !== undefined) query.set('ordemSeed', ordemSeed)
  return `?${query.toString()}`
}

export const PUBLIC_CATALOG_CACHE_TAG = 'public-catalog'
const cached = {
  next: { revalidate: 3600, tags: [PUBLIC_CATALOG_CACHE_TAG] },
}
export const PUBLIC_HOME_CATEGORIES_CACHE_TAG = 'public-home-categories'
const homeCategoriesCached = {
  next: { revalidate: 3600, tags: [PUBLIC_HOME_CATEGORIES_CACHE_TAG] },
}
const dynamic = { cache: 'no-store' as const }

export async function listarPublicosPorEstado(
  uf: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  const raw = await requestJson<RawList>(
    `/acompanhantes/${encodeURIComponent(uf)}${listQuery(pagina, tamanho, ordemSeed)}`,
    dynamic
  )
  return mapList(raw)
}

export async function listarPublicosPorCidade(
  uf: string,
  cidade: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  const raw = await requestJson<RawList>(
    `/acompanhantes/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}${listQuery(pagina, tamanho, ordemSeed)}`,
    dynamic
  )
  return mapList(raw)
}

export async function obterAgregadoPublicoCidade(uf: string, cidade: string): Promise<PublicCatalogCityAggregate> {
  const raw = await requestJson<RawCityAggregate>(
    `/localidades/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}`,
    cached
  )
  return {
    estadoUf: raw.estadoUf,
    estadoNome: raw.estadoNome,
    cidadeNome: raw.cidadeNome,
    cidadeSlug: raw.cidadeSlug,
    totalAnunciosAtivos: raw.totalAnunciosAtivos,
    ultimaAtualizacao: raw.ultimaAtualizacao ?? null,
    bairros: raw.bairros.map((bairro) => ({
      bairroNome: bairro.nome,
      bairroSlug: bairro.slug,
      quantidadeAnuncios: bairro.totalAnunciosAtivos,
      ultimaAtualizacao: bairro.ultimaAtualizacao ?? null,
    })),
    categoriasPrincipais: raw.categorias.map((categoria) => ({
      codigo: categoria.codigo,
      nome: categoria.nome,
      quantidadeAnuncios: categoria.totalAnunciosAtivos,
    })),
    cidadesRelacionadas: raw.cidadesRelacionadas.map((relacionada) => ({
      estadoUf: raw.estadoUf,
      cidadeNome: relacionada.nome,
      cidadeSlug: relacionada.slug,
      totalAnunciosAtivos: relacionada.totalAnunciosAtivos,
      ultimaAtualizacao: relacionada.ultimaAtualizacao ?? null,
    })),
  }
}

export async function listarPublicosPorBairro(
  uf: string,
  cidade: string,
  bairro: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  const raw = await requestJson<RawList>(
    `/acompanhantes/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}/${encodeURIComponent(bairro)}${listQuery(pagina, tamanho, ordemSeed)}`,
    dynamic
  )
  return mapList(raw)
}

export async function obterAnuncioPublicoPorSlug(slug: string) {
  const raw = await requestJson<RawCard>(`/anuncios/${encodeURIComponent(slug)}`, { cache: 'no-store' })
  return {
    ...mapCard(raw),
    descricao: raw.descricao ?? null,
    username: raw.username ?? null,
    idade: raw.idade ?? null,
    idadeOculta: Boolean(raw.idadeOculta),
    contatoPublico: raw.contatoPublico ?? null,
    pendenciaContatoPublico: raw.pendenciaContatoPublico ?? null,
    indexavelSeo: raw.seo?.indexavelFuturo === true,
  } satisfies PublicCatalogDetail
}

export async function listarAnunciosPublicos(
  categoria?: string,
  busca?: string,
  pagina = 0,
  tamanho = 50,
  ordemSeed?: string,
): Promise<PublicCategoryList> {
  const query = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  if (categoria && categoria !== 'TODOS') query.set('categoria', categoria)
  if (busca?.trim()) query.set('busca', busca.trim())
  if (ordemSeed !== undefined) query.set('ordemSeed', ordemSeed)
  const raw = await requestJson<RawCategoryList>(`/anuncios?${query.toString()}`, dynamic)
  return {
    itens: raw.itens.map(mapCard),
    paginacao: raw.paginacao,
    categoria: raw.categoria ?? null,
  }
}

export async function listarCategoriasHomePublicas() {
  const payload = await requestJson<unknown>('/categorias-home', homeCategoriesCached)
  return requireArrayPayload<PublicHomeCategory>(payload)
}

export async function descobrirLocalidadesPublicas() {
  return requestJson<PublicCatalogDiscovery>('/localidades', cached)
}

export async function descobrirAnunciosIndexaveisSitemap() {
  return requestJson<PublicSitemapEntry[]>('/seo/sitemap', cached)
}
