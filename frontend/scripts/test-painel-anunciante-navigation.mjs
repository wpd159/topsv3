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

function listSourceFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name)
    return entry.isDirectory() ? listSourceFiles(absolute) : [absolute]
  })
}

const navigationPath = path.join(
  sourceRoot,
  'components/painel-anunciante/painel-navigation.ts'
)
const navigationSource = fs.readFileSync(navigationPath, 'utf8')
const compiledNavigation = ts.transpileModule(navigationSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
  },
}).outputText
const navigationModule = { exports: {} }
vm.runInNewContext(compiledNavigation, {
  module: navigationModule,
  exports: navigationModule.exports,
})

const { PAINEL_NAV_ITEMS, isPainelNavigationItemActive } = navigationModule.exports
const expectedItems = [
  ['Visão geral', '/painel'],
  ['Meus anúncios', '/meus-anuncios'],
  ['Performance', '/painel/performance'],
  ['Créditos e planos', '/creditos'],
  ['Recomendações', '/painel#recomendacoes'],
  ['Conta e configurações', '/minha-conta'],
]

assert.equal(PAINEL_NAV_ITEMS.length, 6, 'O painel deve expor exatamente seis itens.')
assert.deepEqual(
  JSON.parse(JSON.stringify(PAINEL_NAV_ITEMS.map(({ label, href }) => [label, href]))),
  expectedItems,
  'Ordem ou destino da navegação canônica divergiu.'
)
assert.ok(PAINEL_NAV_ITEMS.every(({ href }) => href && href !== '#'))

function activeIds(pathname) {
  return JSON.parse(
    JSON.stringify(
      PAINEL_NAV_ITEMS
        .filter((item) => isPainelNavigationItemActive(item, pathname))
        .map((item) => item.id)
    )
  )
}

assert.deepEqual(activeIds('/painel'), ['overview'])
assert.deepEqual(activeIds('/painel/performance'), ['performance'])
assert.deepEqual(activeIds('/painel/performance/periodo'), ['performance'])
assert.deepEqual(activeIds('/meus-anuncios'), ['ads'])
assert.deepEqual(activeIds('/meus-anuncios/anuncio-teste'), ['ads'])
assert.deepEqual(activeIds('/meus-anuncios/anuncio-teste/editar'), ['ads'])
assert.deepEqual(activeIds('/meus-anuncios/anuncio-teste/monetizar'), ['ads'])
assert.deepEqual(activeIds('/creditos'), ['credits'])
assert.deepEqual(activeIds('/creditos/historico'), ['credits'])
assert.deepEqual(activeIds('/minha-conta'), ['account'])
assert.deepEqual(activeIds('/minha-conta/seguranca'), ['account'])
assert.equal(
  PAINEL_NAV_ITEMS.some(
    (item) => item.id === 'recommendations' && isPainelNavigationItemActive(item, '/painel')
  ),
  false,
  'A âncora de Recomendações não pode receber aria-current.'
)

const sourceFiles = listSourceFiles(sourceRoot).filter((file) => /\.(?:ts|tsx)$/.test(file))
const combinedSource = sourceFiles.map((file) => fs.readFileSync(file, 'utf8')).join('\n')
assert.equal(
  (combinedSource.match(/export const PAINEL_NAV_ITEMS\b/g) || []).length,
  1,
  'A definição da navegação deve existir uma única vez.'
)

const shell = source('components/painel-anunciante/painel-shell.tsx')
assert.match(shell, /PAINEL_NAV_ITEMS\.map/)
assert.doesNotMatch(shell, /const\s+NAV_ITEMS\s*=/)
assert.match(shell, /<nav/)
assert.match(shell, /aria-label="Navegação principal do painel do anunciante"/)
assert.match(shell, /aria-current=\{isActive \? 'page' : undefined\}/)
assert.match(shell, /focus-visible:ring-2/)
assert.match(shell, /overflow-x-auto/)
assert.match(shell, /whitespace-nowrap/)
assert.match(shell, /activeItemRef/)
assert.match(shell, /navigation\.scrollTo/)
assert.doesNotMatch(shell, /document\.body\.style\.overflow/)
assert.doesNotMatch(shell, /w-\[100vw\]|width\s*:\s*100vw/)

const panel = source('app/(private-routes)/painel/page.tsx')
assert.match(panel, /id="recomendacoes"/)
assert.match(panel, /aria-labelledby="recomendacoes-title"/)
assert.match(panel, /ContractState error=\{recommendationsPendingError\}/)
assert.doesNotMatch(panel, /href="\/painel#recomendacoes"[^>]*aria-current/)

const privateCreditsPath = path.join(
  sourceRoot,
  'app/(private-routes)/creditos/page.tsx'
)
const publicCreditsPath = path.join(
  sourceRoot,
  'app/(public-routes)/creditos/page.tsx'
)
assert.ok(fs.existsSync(privateCreditsPath), '/creditos deve permanecer no layout autenticado.')
assert.ok(!fs.existsSync(publicCreditsPath), '/creditos não pode manter o redirect público antigo.')
const credits = fs.readFileSync(privateCreditsPath, 'utf8')
assert.match(credits, /title="Créditos e planos"/)
assert.match(credits, /<PainelShell/)
assert.match(credits, /ContractState error=\{creditosPendingError\}/)
assert.match(credits, /'INTEGRATION_MISSING'/)
assert.doesNotMatch(credits, /\bredirect\s*\(/)
assert.doesNotMatch(credits, /R\$|pacotes?|saldo|checkout|Efí|EFI|fetch\s*\(/i)
assert.doesNotMatch(credits, /endpoint|backend|stack|arquitetura/i)

const performance = source('app/(private-routes)/painel/performance/page.tsx')
assert.match(performance, /if \(!data\) return \[\]/)
assert.match(performance, /error \|\| !data/)
assert.match(performance, /cards\.map/)

const publicChrome = source('components/layout/public-chrome.tsx')
assert.doesNotMatch(publicChrome, /SitePopupManager/)
const popupManager = source('components/site/site-popup-manager.tsx')
assert.match(popupManager, /export type SiteNotice/)
assert.match(popupManager, /export function SitePopupManager/)
assert.match(popupManager, /if \(!notice\) return null/)
assert.doesNotMatch(popupManager, /PENDING_BACKEND_CONTRACTS|pendingContractError|ContractState/)
assert.doesNotMatch(popupManager, /fetch\s*\(|setInterval\s*\(/)

for (const route of ['/painel', '/meus-anuncios', '/painel/performance', '/creditos', '/minha-conta']) {
  assert.ok(
    PAINEL_NAV_ITEMS.some((item) => item.href === route),
    `Destino autenticado ausente na navegação: ${route}`
  )
}

console.log('PAINEL_ANUNCIANTE_NAVIGATION_RESULT=OK')
