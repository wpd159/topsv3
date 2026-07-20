import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const catalogPath = new URL('../src/lib/public-catalog-api.ts', import.meta.url)
const actionPath = new URL('../src/app/(painel-admin)/admin/categorias-home/actions.ts', import.meta.url)
const pagePath = new URL('../src/app/(painel-admin)/admin/categorias-home/page.tsx', import.meta.url)

const [catalogSource, actionSource, pageSource] = await Promise.all([
  readFile(catalogPath, 'utf8'),
  readFile(actionPath, 'utf8'),
  readFile(pagePath, 'utf8'),
])

assert.match(
  catalogSource,
  /PUBLIC_HOME_CATEGORIES_CACHE_TAG\s*=\s*['"]public-home-categories['"]/,
  'o cache das categorias deve possuir uma tag exclusiva',
)
assert.match(
  catalogSource,
  /requestJson<unknown>\('\/categorias-home',\s*homeCategoriesCached\)/,
  'o contrato publico de categorias deve usar somente o cache identificado',
)
assert.match(
  catalogSource,
  /requireArrayPayload<PublicHomeCategory>\(payload\)/,
  'o contrato publico de categorias deve rejeitar payload incompativel',
)
assert.match(
  actionSource,
  /revalidateTag\(PUBLIC_HOME_CATEGORIES_CACHE_TAG\)/,
  'a acao administrativa deve invalidar a tag das categorias',
)

const mutationCount = pageSource.match(/await salvarCategoriaHomeAdmin\(/g)?.length ?? 0
const invalidationCount = pageSource.match(/await revalidarCacheCategoriasHome\(\)/g)?.length ?? 0

assert.equal(mutationCount, 2, 'o CRUD deve manter os dois caminhos de mutacao existentes')
assert.equal(
  invalidationCount,
  mutationCount,
  'cada mutacao administrativa deve invalidar o cache depois de concluir',
)
assert.match(
  catalogSource,
  /descobrirLocalidadesPublicas\(\)[\s\S]*?'\/localidades', cached/,
  'o cache de localidades deve permanecer independente',
)
assert.match(
  catalogSource,
  /descobrirAnunciosIndexaveisSitemap\(\)[\s\S]*?'\/seo\/sitemap', cached/,
  'o cache do sitemap deve permanecer independente',
)

console.log('OK_CATEGORIAS_HOME_CACHE')
