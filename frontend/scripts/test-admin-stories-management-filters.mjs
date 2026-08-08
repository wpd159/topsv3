import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDirectory, '../src')

function source(relativePath) {
  return readFileSync(path.resolve(sourceRoot, relativePath), 'utf8')
}

const ads = source('features/admin-anuncios/admin-anuncios-list.tsx')
const adsApi = source('features/admin-anuncios/api.ts')
const adsTypes = source('features/admin-anuncios/types.ts')
const management = source('components/stories/admin-stories-management.tsx')
const managementApi = source('lib/admin-story-management-api.ts')

assert.match(ads, /function AdminStoryQuickAction/)
assert.match(ads, /Adicionar aos Stories/)
assert.match(ads, /item\.storyAcao/)
assert.match(ads, /action\.estado === 'ELEGIVEL'/)
assert.match(ads, /action\.estado === 'ATIVO'/)
assert.match(ads, /Story ativo/)
assert.match(ads, /Expira em \{dateLabel\(action\.expiraEm\)\}/)
assert.match(ads, /title=!\{?enabled|title=\{!enabled \? reason : undefined\}/)
assert.match(ads, /disabled=\{!enabled \|\| busy\}/)
assert.match(ads, /publishingStoryIds\.current\.has\(item\.id\)/)
assert.match(ads, /storyIdempotencyKeys\.current/)
assert.match(ads, /current\.itens\.map\(\(row\) => row\.id === item\.id/)
assert.match(ads, /storyAcao:\s*\{[\s\S]*estado: 'ATIVO'/)
assert.match(adsApi, /request<AdminStoryPublication>\('\/stories'/)
assert.match(adsApi, /'Idempotency-Key': idempotencyKey/)
assert.ok(!adsApi.includes('/stories/selecao'))
assert.match(adsTypes, /estado: 'ELEGIVEL' \| 'ATIVO' \| 'INELEGIVEL'/)

const mobilePremium = ads.indexOf('<AdminAnuncioPremiumRapido', ads.indexOf('md:hidden'))
const mobileStory = ads.indexOf('<AdminStoryQuickAction', mobilePremium)
assert.ok(mobilePremium >= 0 && mobileStory > mobilePremium)
const desktopPremium = ads.indexOf('<AdminAnuncioPremiumRapido', ads.indexOf('md:block'))
const desktopStory = ads.indexOf('<AdminStoryQuickAction', desktopPremium)
assert.ok(desktopPremium >= 0 && desktopStory > desktopPremium)

assert.match(management, /useSearchParams\(\)/)
assert.match(management, /searchParams\.get\('usuarioId'\)/)
assert.match(management, /searchParams\.get\('busca'\)/)
assert.match(management, /searchParams\.get\('pagina'\)/)
assert.match(management, /listAdminUsers\(\{ \.\.\.USER_LOOKUP_FILTERS, termo:/)
assert.match(management, /size: 10/)
assert.match(management, /role="combobox"/)
assert.match(management, /role="listbox"/)
assert.match(management, /usuarioId: selectedUserId \|\| null/)
assert.match(management, /busca: searchDraft\.trim\(\) \|\| null/)
assert.match(management, /pagina: 0/)
assert.match(management, /router\.push\(pathname\)/)
assert.match(management, /updateQuery\(\{ pagina: filters\.page [+-] 1 \}\)/)
assert.match(management, /setReload\(\(value\) => value \+ 1\)/)
assert.match(management, /Nenhum Story encontrado para os filtros informados\./)
assert.match(management, /grid min-w-0 gap-3/)
assert.match(management, /className="w-full"/)
assert.ok(!management.includes('setInterval('))

assert.match(managementApi, /request<AdminStoriesPagina>\(`\/gestao\?\$\{query\.toString\(\)\}`\)/)
assert.match(managementApi, /query\.set\('usuarioId', usuarioId\.trim\(\)\)/)
assert.match(managementApi, /query\.set\('busca', busca\.trim\(\)\)/)
assert.ok(!managementApi.includes('/stories/selecao'))

console.log('ADMIN_STORIES_MANAGEMENT_FILTERS_RESULT=OK')
