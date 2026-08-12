import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = path.join(frontendRoot, 'src')

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

function normalize(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

const page = source('app/(painel-admin)/admin/compliance/page.tsx')
const sidebar = source('app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx')
const sidebarUtils = source('app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts')
const normalizedPage = normalize(page)
const normalizedSidebar = normalize(sidebar)

for (const forbidden of [
  'Integracao pendente',
  'PENDING_BACKEND_CONTRACTS',
  'usePendingContractActions',
  'Auditoria administrativa',
  'Aceites juridicos',
  'Eventos criticos',
  'Configuracoes de compliance',
  'Score minimo',
  'Retencao em dias',
  'Salvar configuracoes',
]) {
  assert.ok(!normalizedPage.includes(forbidden), 'Placeholder de Compliance ainda presente: ' + forbidden)
}

for (const control of ['<Table', '<Input', '<Select', '<Textarea']) {
  assert.ok(!page.includes(control), 'Controle simulado ainda presente no hub: ' + control)
}

const areas = [
  ['Logs visitantes', '/admin/compliance#visitor-logs'],
  ['Risco por sessao', '/admin/compliance#visitor-risk'],
  ['Documentos visitantes', '/admin/compliance#visitor-document-fallback'],
]

for (const [label, href] of areas) {
  assert.ok(normalizedPage.includes(label), 'Área real ausente: ' + label)
  assert.ok(page.includes("href: '" + href + "'"), 'Destino canônico ausente: ' + href)
}

assert.equal((page.match(/href: '\/admin\/compliance#/g) || []).length, 3)
assert.ok(normalizedPage.includes('Monitoramento'))
assert.ok(normalizedPage.includes('Evidencias e governanca'))
assert.ok(page.includes('grid grid-cols-1 gap-3 md:grid-cols-2'))
assert.ok(page.includes('w-full sm:w-auto'))
assert.ok(page.includes("addEventListener('hashchange'"))
assert.ok(page.includes("addEventListener('popstate'"))
assert.ok(page.includes('<Link href={item.href}>'))
assert.ok(page.includes('<Link href="/admin/compliance"'))
assert.ok(normalizedPage.includes('Voltar ao hub'))
assert.ok(!page.includes('fetch('), 'O hub não deve criar uma fonte de dados paralela.')

for (const component of ['VisitorAgeLogs', 'VisitorRisk', 'VisitorDocuments']) {
  assert.ok(page.includes('import { ' + component + ' }'), 'Componente real não importado: ' + component)
  assert.ok(page.includes('<' + component + ' />'), 'Componente real não renderizado: ' + component)
}

for (const removed of ['Auditoria administrativa', 'Aceites juridicos', 'Configuracoes de compliance']) {
  assert.ok(!normalizedSidebar.includes(removed), 'Atalho sem contrato ainda presente: ' + removed)
}

for (const retained of [
  "label: 'Compliance'",
  "href: '/admin/compliance'",
  "label: 'Logs visitantes'",
  "href: '/admin/compliance#visitor-logs'",
  "label: 'Documentos visitantes'",
  "href: '/admin/compliance#visitor-document-fallback'",
]) {
  assert.ok(sidebar.includes(retained), 'Atalho real ausente: ' + retained)
}

assert.ok(sidebarUtils.includes("'/admin/compliance'"), 'RBAC do frontend deve refletir o backend ADMIN-only.')

console.log('ADMIN_COMPLIANCE_HUB_RESULT=OK')
