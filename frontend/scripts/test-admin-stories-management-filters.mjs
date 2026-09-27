import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import ts from 'typescript'

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
assert.match(managementApi, /statusAdministrativo: string/)
assert.match(managementApi, /ativo: boolean/)
assert.match(managementApi, /removivel: boolean/)
assert.match(managementApi, /class AdminStoryManagementError extends Error/)
assert.match(managementApi, /readonly status: number/)
assert.match(management, /story\.statusAdministrativo === 'ATIVO'/)
assert.match(management, /story\.ativo \? 'bg-emerald-100/)
assert.match(management, /\{story\.removivel \? \(/)
assert.match(management, /Histórico preservado/)
assert.ok(!management.includes('!story.encerradoEm ?'))
assert.match(management, /cause instanceof AdminStoryManagementError && cause\.status === 409/)
assert.match(management, /const refreshed = await fetchAdminStories\(filters\)/)
assert.match(management, /refreshed\.itens\.find\(\(item\) => item\.id === selected\.id\)/)
assert.match(management, /current\.itens\.map\(\(item\) => item\.id === currentStory\.id/)
assert.match(management, /overflow-x-auto/)
assert.match(management, /w-\[calc\(100vw-1rem\)\]/)

assert.match(managementApi, /request<AdminStoriesPagina>\(`\/gestao\?\$\{query\.toString\(\)\}`\)/)
assert.match(managementApi, /query\.set\('usuarioId', usuarioId\.trim\(\)\)/)
assert.match(managementApi, /query\.set\('busca', busca\.trim\(\)\)/)
assert.ok(!managementApi.includes('/stories/selecao'))

// Execute the real list callbacks with synthetic API responses. This checks the
// publication message and retained rows, not just the source shape above.
function runtimeModule(name, imports = {}) {
  const compiled = ts.transpileModule(source(name), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    fileName: name, reportDiagnostics: true,
  })
  assert.deepEqual((compiled.diagnostics ?? []).filter((item) => item.category === ts.DiagnosticCategory.Error), [])
  const module = { exports: {} }
  new Function('require', 'module', 'exports', compiled.outputText)((specifier) => {
    assert.ok(Object.hasOwn(imports, specifier), `Fronteira não declarada: ${name}: ${specifier}`)
    return imports[specifier]
  }, module, module.exports)
  return module.exports
}

function componentHooks() {
  const slots = [], effects = []
  let index = 0, dirty = false, component, tree
  const changed = (previous, next) => !previous || !next || next.some((value, i) => !Object.is(value, previous[i]))
  const react = {
    useState(initial) {
      const slot = index++
      if (!slots[slot]) slots[slot] = { value: typeof initial === 'function' ? initial() : initial }
      return [slots[slot].value, (next) => {
        const value = typeof next === 'function' ? next(slots[slot].value) : next
        dirty ||= !Object.is(value, slots[slot].value)
        slots[slot].value = value
      }]
    },
    useRef(initial) { const slot = index++; return slots[slot] ??= { current: initial } },
    useMemo(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) slots[slot] = { value: callback(), dependencies }
      return slots[slot].value
    },
    useCallback(callback, dependencies) { return react.useMemo(() => callback, dependencies) },
    useEffect(callback, dependencies) {
      const slot = index++
      if (!slots[slot] || changed(slots[slot].dependencies, dependencies)) {
        const old = slots[slot]
        slots[slot] = { dependencies, cleanup: old?.cleanup }
        effects.push(() => { old?.cleanup?.(); slots[slot].cleanup = callback() })
      }
    },
  }
  function render() {
    index = 0; dirty = false; tree = component({})
    effects.splice(0).forEach((effect) => effect())
  }
  return {
    react,
    mount(next) { component = next; render() },
    async settle() {
      for (let iteration = 0; iteration < 12; iteration++) {
        await new Promise((resolve) => setImmediate(resolve))
        if (dirty) render()
      }
      assert.equal(dirty, false, 'A lista deve estabilizar sem efeitos em ciclo.')
    },
    get tree() { return tree },
    unmount() { slots.forEach((slot) => slot?.cleanup?.()) },
  }
}

const element = (type, props) => ({ type, props: props ?? {} })
const jsxRuntime = { jsx: element, jsxs: element, Fragment: 'Fragment' }
const icons = new Proxy({}, { get: (_, key) => String(key) })
const contractRuntime = runtimeModule('lib/api-contract.ts')
const feedbackRuntime = runtimeModule('components/feedback/contract-state.tsx', {
  react: {}, 'react/jsx-runtime': jsxRuntime, 'lucide-react': icons,
  '@/components/ui/button': { Button: 'Button' }, '@/lib/api-contract': contractRuntime,
})
function nodes(tree) {
  if (tree == null || typeof tree === 'boolean') return []
  if (Array.isArray(tree)) return tree.flatMap(nodes)
  if (typeof tree !== 'object') return [tree]
  if (typeof tree.type === 'function') return nodes(tree.type(tree.props))
  return [tree, ...nodes(tree.props?.children)]
}
const visibleText = (tree) => nodes(tree).filter((node) => typeof node === 'string' || typeof node === 'number').join(' ')
const controls = (tree, predicate) => nodes(tree).filter((node) => typeof node === 'object' && predicate(node))
const publishButtons = (tree) => controls(tree, (node) => node.type === 'Button' && /Adicionar aos Stories|Adicionando\.\.\./.test(visibleText(node)))
const syntheticAd = {
  id: 'synthetic-ad', slug: 'anuncio-sintetico', titulo: 'Anúncio sintético preservado',
  status: 'PUBLICADO', statusModeracao: 'APROVADO', criadoEm: '2026-01-01T00:00:00Z',
  anunciante: { id: 'synthetic-owner', status: 'ATIVO' }, beneficiosPremium: [],
  storyAcao: { estado: 'ELEGIVEL', storyId: null, expiraEm: null, motivo: null },
}
const syntheticPage = { itens: [syntheticAd], page: 0, size: 20, totalElements: 1, totalPages: 1, last: true }

async function mountList({ publish, listError, admin = true } = {}) {
  const runner = componentHooks(), calls = []
  const imports = {
    react: runner.react, 'react/jsx-runtime': jsxRuntime, 'lucide-react': icons,
    'next/link': { default: 'Link' }, 'next/image': { default: 'Image' },
    '@/lib/admin-auth-api': { getAdminSession: async () => ({ papeis: [admin ? 'ADMIN' : 'MODERADOR'], permissoes: [] }) },
    '@/lib/api-contract': contractRuntime, '@/components/feedback/contract-state': feedbackRuntime,
    '@/lib/phone-mask': { maskPhoneBR: (value) => value }, '@/lib/media/public-media': { imagemPublicaR2: () => false },
    '@/lib/seo/indexnow-client': {}, '@/features/anuncio-wizard/components/searchable-select': { SearchableSelect: 'SearchableSelect' },
    './admin-anuncio-premium-rapido': { AdminAnuncioPremiumRapido: 'AdminAnuncioPremiumRapido', premiumBenefitGranted: () => false },
    './queue-context': runtimeModule('features/admin-anuncios/queue-context.ts'),
    './api': {
      async listAdminAds() { calls.push(['LIST']); if (listError) throw listError; return syntheticPage },
      async listAdminAdFilterLocations() { return [] }, async listAdminPremiumCatalog() { return [] },
      async publishAdminStory(id, key) { calls.push(['PUBLISH', id, key]); assert.ok(publish, 'Publicação inesperada.'); return publish(id, key) },
    },
  }
  for (const [module, names] of Object.entries({
    button: ['Button'], input: ['Input'], badge: ['Badge'],
    select: ['Select', 'SelectContent', 'SelectItem', 'SelectTrigger', 'SelectValue'],
  })) imports[`@/components/ui/${module}`] = Object.fromEntries(names.map((name) => [name, name]))
  runner.mount(runtimeModule('features/admin-anuncios/admin-anuncios-list.tsx', imports).AdminAnunciosList)
  await runner.settle()
  return { runner, calls }
}

const serverFailure = await contractRuntime.apiErrorFromResponse(new Response(null, { status: 500 }))
const networkFailure = new contractRuntime.ApiContractError('Falha sintética de rede.', 'NETWORK_FAILURE', null, true)
const publicationFallback = 'A publicação não foi confirmada. Tente novamente.'
for (const [failure, expected] of [
  [serverFailure, publicationFallback], [networkFailure, publicationFallback],
  [new contractRuntime.ApiContractError('Revise o anúncio sintético.', 'INVALID_REQUEST', 400), 'Revise o anúncio sintético.'],
  [new contractRuntime.ApiContractError('Acesso sintético negado.', 'ACCESS_DENIED', 403), 'Acesso sintético negado.'],
  [new contractRuntime.ApiContractError('Story sintético em conflito.', 'CONFLICT', 409), 'Story sintético em conflito.'],
]) {
  const failed = await mountList({ publish: async () => { throw failure } })
  publishButtons(failed.runner.tree)[0].props.onClick()
  await failed.runner.settle()
  const text = visibleText(failed.runner.tree)
  assert.ok(text.includes('Não foi possível publicar o Story'), 'Falha de POST deve identificar a publicação do Story.')
  assert.ok(text.includes(expected))
  assert.doesNotMatch(text, /N[aã]o foi poss[ií]vel carregar|Story ativo/)
  assert.ok(text.includes(syntheticAd.titulo), 'Falha de publicação preserva a lista carregada.')
  assert.equal(controls(failed.runner.tree, (node) => node.type === 'table').length, 1)
  assert.deepEqual(failed.calls.map(([kind]) => kind), ['LIST', 'PUBLISH'], 'Falha do POST não é falha de GET nem dispara publicação automática.')
  assert.ok(publishButtons(failed.runner.tree).every((button) => !button.props.disabled))
  failed.runner.unmount()
}

let rejectFirst
const pendingPublication = new Promise((_, reject) => { rejectFirst = reject })
let publicationAttempts = 0
const retried = await mountList({ publish: async () => {
  if (++publicationAttempts === 1) return pendingPublication
  return { storyId: 'synthetic-story', fimEm: '2026-01-02T00:00:00Z' }
} })
const firstButton = publishButtons(retried.runner.tree)[0]
firstButton.props.onClick()
firstButton.props.onClick()
await retried.runner.settle()
assert.equal(publicationAttempts, 1, 'Duplo clique não duplica a publicação em andamento.')
assert.ok(publishButtons(retried.runner.tree).every((button) => button.props.disabled))
assert.doesNotMatch(visibleText(retried.runner.tree), /Story ativo/)
rejectFirst(serverFailure)
await retried.runner.settle()
publishButtons(retried.runner.tree)[0].props.onClick()
await retried.runner.settle()
assert.deepEqual(retried.calls.map(([kind]) => kind), ['LIST', 'PUBLISH', 'PUBLISH'])
assert.equal(retried.calls[1][2], retried.calls[2][2], 'Nova tentativa após falha conserva a chave idempotente.')
assert.match(visibleText(retried.runner.tree), /Story ativo/)
assert.doesNotMatch(visibleText(retried.runner.tree), /Não foi possível publicar|publicação não foi confirmada/)
assert.equal(publishButtons(retried.runner.tree).length, 0)
retried.runner.unmount()

const loadFailed = await mountList({ listError: serverFailure })
assert.match(visibleText(loadFailed.runner.tree), /Nao foi possivel carregar/)
assert.doesNotMatch(visibleText(loadFailed.runner.tree), /Não foi possível publicar/)
assert.deepEqual(loadFailed.calls, [['LIST']])
loadFailed.runner.unmount()
const denied = await mountList({ admin: false })
const deniedButton = publishButtons(denied.runner.tree)[0]
assert.equal(deniedButton.props.disabled, true)
deniedButton.props.onClick()
await denied.runner.settle()
assert.deepEqual(denied.calls, [['LIST']], 'A correção mantém publicação administrativa restrita a ADMIN.')
denied.runner.unmount()
console.log('ADMIN_STORY_PUBLICATION_ERRORS_RESULT=OK cases=8 componentCallbacks=real transport=synthetic listPreserved=true idempotencyPreserved=true')
console.log('ADMIN_STORIES_MANAGEMENT_FILTERS_RESULT=OK')
