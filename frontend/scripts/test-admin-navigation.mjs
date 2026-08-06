import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require('typescript')
const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

function loadTypeScriptModule(relativePath) {
  const compiled = ts.transpileModule(source(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText
  const module = { exports: {} }
  vm.runInNewContext(compiled, { module, exports: module.exports, URL, URLSearchParams })
  return module.exports
}

const navigation = loadTypeScriptModule('lib/admin-navigation.ts')
const sidebarUtils = loadTypeScriptModule('app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts')
const monetizacaoNavigation = loadTypeScriptModule('lib/admin-monetizacao-navigation.ts')
const sidebarSource = source('app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx')
const sidebarView = source('app/(painel-admin)/admin/components/sidebar/sidebar.tsx')
const sidebarNav = source('app/(painel-admin)/admin/components/sidebar/sidebar-nav.tsx')
const login = source('app/(admin-auth)/admin/login/page.tsx')
const adminRoot = source('app/(painel-admin)/admin/page.tsx')
const compliance = source('app/(painel-admin)/admin/compliance/page.tsx')
const credits = source('app/(painel-admin)/admin/creditos/page.tsx')
const premiumRedirect = source('app/(painel-admin)/admin/beneficios-premium/page.tsx')

assert.equal(navigation.ADMIN_DASHBOARD_PATH, '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath(null), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('/admin'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('/admin/login'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('/admin/anuncios?fila=1'), '/admin/anuncios?fila=1')
assert.equal(navigation.resolveAdminPostLoginPath('/admin/creditos?aba=stories'), '/admin/creditos?aba=stories')
assert.equal(navigation.resolveAdminPostLoginPath('/anuncios'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('//example.com/admin'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('/admin/%2e%2e/anuncios'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginSearch('?next=%2Fadmin%2Fcompliance%23visitor-logs'), '/admin/compliance#visitor-logs')

const anuncioId = '7102da3e-7b59-6246-276e-35f128b9d82f'
assert.equal(monetizacaoNavigation.adminMonetizacaoAba(new URLSearchParams('aba=stories')), 'stories')
assert.equal(monetizacaoNavigation.adminMonetizacaoAba(new URLSearchParams('aba=invalida')), 'beneficios')
assert.equal(monetizacaoNavigation.adminMonetizacaoAba(new URLSearchParams('aba=stories&aba=beneficios')), 'beneficios')
assert.equal(
  monetizacaoNavigation.adminMonetizacaoQuery(
    new URLSearchParams(`foo=descartar&aba=stories&anuncioId=${anuncioId}`),
    'stories',
  ),
  `aba=stories&anuncioId=${anuncioId}`,
)
assert.equal(
  monetizacaoNavigation.adminMonetizacaoQuery(
    new URLSearchParams(`anuncioId=${anuncioId}&anuncioId=${anuncioId}`),
    'beneficios',
  ),
  'aba=beneficios',
)
assert.equal(monetizacaoNavigation.anuncioIdLegadoSeguro(anuncioId), anuncioId)
assert.equal(monetizacaoNavigation.anuncioIdLegadoSeguro([anuncioId, anuncioId]), null)
assert.equal(monetizacaoNavigation.anuncioIdLegadoSeguro('https://example.com'), null)
const hrefs = ['/admin/dashboard', '/admin/creditos', '/admin/compliance', '/admin/compliance#admin-logs', '/admin/compliance#legal-acceptances']
assert.equal(navigation.activeAdminSidebarHref(hrefs, '/admin/compliance', '#legal-acceptances'), '/admin/compliance#legal-acceptances')
assert.equal(navigation.activeAdminSidebarHref(hrefs, '/admin/compliance', '#unknown'), '/admin/compliance')
assert.equal(navigation.activeAdminSidebarHref(hrefs, '/admin/creditos', ''), '/admin/creditos')

const menuEntries = [...sidebarSource.matchAll(/label:\s*'([^']+)'[\s\S]*?href:\s*'([^']+)'[\s\S]*?section:\s*'([^']+)'/g)]
  .map((match) => ({ label: match[1], href: match[2], section: match[3] }))

assert.equal(menuEntries.length, 22, 'O menu administrativo deve manter os 22 itens canônicos.')
assert.equal(new Set(menuEntries.map((item) => item.href)).size, 22, 'Os destinos do menu devem ser únicos.')
assert.deepEqual(
  menuEntries.filter((item) => item.section === 'Operação').slice(0, 5).map((item) => item.label),
  ['Anúncios', 'Gestão de Stories', 'Usuários', 'Tickets', 'Denúncias'],
)
assert.deepEqual(menuEntries.find((item) => item.label === 'Gestão de Stories'), {
  label: 'Gestão de Stories',
  href: '/admin/stories',
  section: 'Operação',
})
assert.ok(!sidebarSource.includes('/admin/indicacoes'), 'Indicações não pode voltar ao menu.')
assert.equal(menuEntries.filter((item) => item.label === 'Monetização').length, 1)
assert.deepEqual(menuEntries.find((item) => item.label === 'Monetização'), {
  label: 'Monetização',
  href: '/admin/creditos',
  section: 'Monetização',
})
assert.ok(!menuEntries.some((item) => item.label === 'Planos e créditos'))
assert.ok(!menuEntries.some((item) => item.label === 'Benefícios premium'))

const adminMenu = sidebarUtils.filterSidebarLinksByRole(menuEntries, 'ADMIN')
const moderatorMenu = sidebarUtils.filterSidebarLinksByRole(menuEntries, 'MODERADOR')
assert.equal(adminMenu.length, 22)
assert.equal(moderatorMenu.length, 16)
for (const restricted of ['/admin/financeiro', '/admin/creditos', '/admin/termos-footer', '/admin/blog', '/admin/staff', '/admin/stories']) {
  assert.ok(!moderatorMenu.some((item) => item.href === restricted))
}
assert.equal(sidebarUtils.canAccessRoute('/admin/anuncios', 'MODERADOR'), true)
assert.equal(sidebarUtils.canAccessRoute('/admin/compliance#visitor-logs', 'MODERADOR'), true)
assert.equal(sidebarUtils.canAccessRoute('/admin/creditos?aba=beneficios', 'MODERADOR'), false)
assert.equal(sidebarUtils.canAccessRoute('/admin/beneficios-premium', 'MODERADOR'), false)
assert.equal(sidebarUtils.canAccessRoute('/admin/stories', 'MODERADOR'), false)
assert.equal(sidebarUtils.canAccessRoute('/admin/dashboard', 'USUARIO'), false)

assert.ok(!login.includes('/admin/stories'))
assert.match(login, /resolveAdminPostLoginSearch/)
assert.equal((login.match(/router\.replace\(postLoginDestination\(\)\)/g) || []).length, 2)
assert.match(adminRoot, /redirect\(ADMIN_DASHBOARD_PATH\)/)
assert.match(sidebarNav, /activeAdminSidebarHref/)
assert.match(sidebarNav, /aria-current=\{isActive \? 'page' : undefined\}/)
assert.match(sidebarView, /SheetDescription/)
assert.equal((sidebarView.match(/sidebarLinks/g) || []).length, 2)
assert.match(compliance, /sectionFromHash/)
assert.match(compliance, /addEventListener\('popstate'/)
assert.match(credits, /ADMIN_MONETIZACAO_ABAS/)
assert.match(credits, /adminMonetizacaoAba\(searchParams\)/)
assert.match(credits, /adminMonetizacaoQuery\(searchParams, value\)/)
assert.match(credits, /router\.push\(`/)
assert.match(credits, /router\.replace\(`/)
assert.match(premiumRedirect, /new URLSearchParams\(\{ aba: 'beneficios' \}\)/)
assert.match(premiumRedirect, /anuncioIdLegadoSeguro/)
assert.match(premiumRedirect, /redirect\(`\/admin\/creditos\?\$\{destino\.toString\(\)\}`\)/)

console.log('ADMIN_NAVIGATION_RESULT=OK')