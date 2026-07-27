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
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
  }).outputText
  const module = { exports: {} }
  vm.runInNewContext(compiled, {
    module,
    exports: module.exports,
    URL,
    URLSearchParams,
  })
  return module.exports
}

const navigation = loadTypeScriptModule('lib/admin-navigation.ts')
const sidebarUtils = loadTypeScriptModule(
  'app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts'
)
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
assert.equal(
  navigation.resolveAdminPostLoginPath('/admin/anuncios?fila=1'),
  '/admin/anuncios?fila=1'
)
assert.equal(navigation.resolveAdminPostLoginPath('/admin/stories'), '/admin/stories')
assert.equal(navigation.resolveAdminPostLoginPath('/anuncios'), '/admin/dashboard')
assert.equal(navigation.resolveAdminPostLoginPath('//example.com/admin'), '/admin/dashboard')
assert.equal(
  navigation.resolveAdminPostLoginPath('/admin/%2e%2e/anuncios'),
  '/admin/dashboard'
)
assert.equal(
  navigation.resolveAdminPostLoginSearch('?next=%2Fadmin%2Fcompliance%23visitor-logs'),
  '/admin/compliance#visitor-logs'
)

const hrefs = [
  '/admin/dashboard',
  '/admin/creditos',
  '/admin/creditos#beneficios-premium',
  '/admin/compliance',
  '/admin/compliance#admin-logs',
  '/admin/compliance#legal-acceptances',
]
assert.equal(
  navigation.activeAdminSidebarHref(hrefs, '/admin/compliance', '#legal-acceptances'),
  '/admin/compliance#legal-acceptances'
)
assert.equal(
  navigation.activeAdminSidebarHref(hrefs, '/admin/compliance', '#unknown'),
  '/admin/compliance'
)
assert.equal(
  navigation.activeAdminSidebarHref(hrefs, '/admin/creditos', '#beneficios-premium'),
  '/admin/creditos#beneficios-premium'
)

const menuEntries = [...sidebarSource.matchAll(
  /label:\s*'([^']+)'[\s\S]*?href:\s*'([^']+)'[\s\S]*?section:\s*'([^']+)'/g
)].map((match) => ({ label: match[1], href: match[2], section: match[3] }))

assert.equal(menuEntries.length, 23, 'O menu administrativo deve manter os 23 itens canônicos.')
assert.equal(
  new Set(menuEntries.map((item) => item.href)).size,
  23,
  'Os destinos do menu devem ser únicos.'
)
assert.ok(!sidebarSource.includes('/admin/stories'), 'Stories administrativos não pode voltar ao menu.')
assert.ok(
  menuEntries.some(
    (item) =>
      item.label === 'Benefícios premium' &&
      item.href === '/admin/creditos#beneficios-premium'
  ),
  'Benefícios premium deve abrir sua seção no fluxo canônico de Créditos.'
)

const adminMenu = sidebarUtils.filterSidebarLinksByRole(menuEntries, 'ADMIN')
const moderatorMenu = sidebarUtils.filterSidebarLinksByRole(menuEntries, 'MODERADOR')
assert.equal(adminMenu.length, 23)
assert.equal(moderatorMenu.length, 16)
for (const restricted of [
  '/admin/financeiro',
  '/admin/creditos',
  '/admin/creditos#beneficios-premium',
  '/admin/indicacoes',
  '/admin/termos-footer',
  '/admin/blog',
  '/admin/staff',
]) {
  assert.ok(!moderatorMenu.some((item) => item.href === restricted))
}
assert.equal(sidebarUtils.canAccessRoute('/admin/anuncios', 'MODERADOR'), true)
assert.equal(sidebarUtils.canAccessRoute('/admin/compliance#visitor-logs', 'MODERADOR'), true)
assert.equal(
  sidebarUtils.canAccessRoute('/admin/creditos#beneficios-premium', 'MODERADOR'),
  false
)
assert.equal(sidebarUtils.canAccessRoute('/admin/stories', 'MODERADOR'), false)
assert.equal(sidebarUtils.canAccessRoute('/admin/dashboard', 'USUARIO'), false)

assert.ok(!login.includes('/admin/stories'))
assert.match(login, /resolveAdminPostLoginSearch/)
assert.equal((login.match(/router\.replace\(postLoginDestination\(\)\)/g) || []).length, 2)
assert.match(adminRoot, /redirect\(ADMIN_DASHBOARD_PATH\)/)
assert.match(sidebarNav, /activeAdminSidebarHref/)
assert.match(sidebarNav, /aria-current=\{isActive \? 'page' : undefined\}/)
assert.match(sidebarNav, /addEventListener\('hashchange'/)
assert.match(sidebarView, /SheetDescription/)
assert.equal((sidebarView.match(/sidebarLinks/g) || []).length, 2)
assert.match(compliance, /sectionFromHash/)
assert.match(compliance, /addEventListener\('popstate'/)
assert.match(compliance, /dispatchEvent\(new Event\('hashchange'\)\)/)
assert.match(credits, /id="beneficios-premium"/)
assert.match(premiumRedirect, /redirect\('\/admin\/creditos#beneficios-premium'\)/)

console.log('ADMIN_NAVIGATION_RESULT=OK')
