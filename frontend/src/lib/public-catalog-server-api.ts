import 'server-only'

import {
  PUBLIC_CATALOG_CACHE_TAG,
  PUBLIC_HOME_CATEGORIES_CACHE_TAG,
  isPublicCatalogNotFound,
  parsePublicCatalogCityAggregate,
  parsePublicCatalogDetail,
  parsePublicCatalogDiscovery,
  parsePublicCatalogList,
  parsePublicCategoryList,
  parsePublicHomeCategories,
  parsePublicSitemapEntries,
} from '@/lib/public-catalog-api'
import { publicServerApiJson, type PublicServerCachePolicy } from '@/lib/public-server-api'

export { isPublicCatalogNotFound }
export type {
  PublicCatalogCityAggregate,
  PublicCatalogDetail,
  PublicCatalogDiscovery,
  PublicCatalogList,
  PublicCategoryList,
  PublicHomeCategory,
  PublicSitemapEntry,
} from '@/lib/public-catalog-api'

const NO_STORE = { mode: 'no-store' } as const
const CATALOG_CACHE = {
  mode: 'revalidate',
  seconds: 3_600,
  tags: [PUBLIC_CATALOG_CACHE_TAG],
} as const
const SEO_CACHE = {
  mode: 'revalidate',
  seconds: 300,
  tags: [PUBLIC_CATALOG_CACHE_TAG, 'public-seo'],
} as const
const HOME_CATEGORIES_CACHE = {
  mode: 'revalidate',
  seconds: 3_600,
  tags: [PUBLIC_HOME_CATEGORIES_CACHE_TAG],
} as const

function listQuery(pagina: number, tamanho: number, ordemSeed?: string) {
  const query = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  if (ordemSeed !== undefined) query.set('ordemSeed', ordemSeed)
  return `?${query.toString()}`
}

function request<T>(
  path: string,
  endpointFamily: string,
  cache: PublicServerCachePolicy,
  validate: (payload: unknown) => T,
) {
  return publicServerApiJson(path, { endpointFamily, cache, validate })
}

export function listarPublicosPorEstado(
  uf: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  return request(
    `/acompanhantes/${encodeURIComponent(uf)}${listQuery(pagina, tamanho, ordemSeed)}`,
    'catalog.list.state',
    NO_STORE,
    parsePublicCatalogList,
  )
}

export function listarPublicosPorCidade(
  uf: string,
  cidade: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  return request(
    `/acompanhantes/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}${listQuery(pagina, tamanho, ordemSeed)}`,
    'catalog.list.city',
    NO_STORE,
    parsePublicCatalogList,
  )
}

export function listarPublicosPorBairro(
  uf: string,
  cidade: string,
  bairro: string,
  pagina = 0,
  tamanho = 20,
  ordemSeed?: string,
) {
  return request(
    `/acompanhantes/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}/${encodeURIComponent(bairro)}${listQuery(pagina, tamanho, ordemSeed)}`,
    'catalog.list.neighborhood',
    NO_STORE,
    parsePublicCatalogList,
  )
}

export function listarAnunciosPublicos(
  categoria?: string,
  busca?: string,
  pagina = 0,
  tamanho = 50,
  ordemSeed?: string,
  anunciante?: string,
) {
  const query = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) })
  if (categoria && categoria !== 'TODOS') query.set('categoria', categoria)
  if (busca?.trim()) query.set('busca', busca.trim())
  if (ordemSeed !== undefined) query.set('ordemSeed', ordemSeed)
  if (anunciante?.trim()) query.set('anunciante', anunciante.trim())
  return request(
    `/anuncios?${query.toString()}`,
    'catalog.list.all',
    NO_STORE,
    parsePublicCategoryList,
  )
}

export function obterAnuncioPublicoPorSlug(slug: string) {
  return request(
    `/anuncios/${encodeURIComponent(slug)}`,
    'catalog.detail',
    NO_STORE,
    parsePublicCatalogDetail,
  )
}

export function obterAgregadoPublicoCidade(uf: string, cidade: string) {
  return request(
    `/localidades/${encodeURIComponent(uf)}/${encodeURIComponent(cidade)}`,
    'catalog.seo.city',
    SEO_CACHE,
    parsePublicCatalogCityAggregate,
  )
}

export function listarCategoriasHomePublicas() {
  return request(
    '/categorias-home',
    'catalog.home-categories',
    HOME_CATEGORIES_CACHE,
    parsePublicHomeCategories,
  )
}

export function descobrirLocalidadesPublicas() {
  return request(
    '/localidades',
    'catalog.locations.discovery',
    CATALOG_CACHE,
    parsePublicCatalogDiscovery,
  )
}

export function listarCatalogoCompletoLocalidades() {
  return request(
    '/localidades/catalogo',
    'catalog.locations.complete',
    CATALOG_CACHE,
    parsePublicCatalogDiscovery,
  )
}

export function descobrirAnunciosIndexaveisSitemap() {
  return request(
    '/seo/sitemap',
    'catalog.sitemap',
    SEO_CACHE,
    parsePublicSitemapEntries,
  )
}
