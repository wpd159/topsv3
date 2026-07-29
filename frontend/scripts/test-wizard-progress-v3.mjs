import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..', '..')
const read = (relative) => readFileSync(path.join(root, relative), 'utf8')
const normalize = (value) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')

const page = normalize(read('frontend/src/app/(painel-admin)/admin/wizard-progress/page.tsx'))
const api = read('frontend/src/lib/admin-wizard-progress-api.ts')
const sync = read('frontend/src/features/anuncio-wizard/wizard-progress.ts')
const wizard = read('frontend/src/features/anuncio-wizard/anuncio-wizard.tsx')
const repository = read(
  'backend/src/main/java/br/com/topsdojob/v3/persistence/repository/wizard/WizardProgressJdbcRepository.java',
)
const migration = read('backend/src/main/resources/db/migration/V043__progresso_wizard_anuncios.sql')
const openapi = read('contracts/openapi/topsdojob-v3-local.yaml')

assert.ok(!page.includes('PENDING_BACKEND_CONTRACTS'))
assert.ok(!page.includes('Integracao pendente'))
assert.ok(page.includes('fetchWizardProgressDashboard'))
for (const label of [
  'Hoje', '7 dias', '30 dias', 'Personalizado', 'Todas as UFs', 'Todas as cidades',
  'Todos os KYC', 'Todos os anuncios', 'Atualizar', 'Tentar novamente',
  'Nenhuma jornada encontrada', 'Abrir anuncio', 'Abrir moderacao', 'Editar usuario',
]) {
  assert.ok(page.includes(label), `Controle real ausente: ${label}`)
}
assert.ok(page.includes('router.push(`${pathname}?${params.toString()}`)'))
assert.ok(page.includes('overflow-x-auto'))
assert.ok(page.includes('sm:grid-cols-2'))

assert.ok(api.includes("adminApiUrl(`/wizard-progress/dashboard?${params.toString()}`)"))
assert.ok(api.includes("credentials: 'include'"))
assert.ok(api.includes("cache: 'no-store'"))
assert.ok(sync.includes("publicApiUrl('/wizard-progress/sync')"))
assert.ok(sync.includes("credentials: 'include'"))
assert.ok(sync.includes('csrfHeaderName()'))
assert.ok(sync.includes("['X', 'XSRF', 'TOKEN'].join('-')"))
assert.ok(sync.includes('window.sessionStorage'))
assert.ok(wizard.includes("syncProgress('concluido', 'AGUARDANDO_MODERACAO'"))
assert.ok(wizard.includes('clearWizardProgressSessionId(progressScope)'))

assert.ok(repository.includes('count(*) FILTER'))
assert.ok(repository.includes('count(*) OVER ()'))
assert.ok(!repository.includes('findById('))
for (const forbidden of ['cpf', 'telefone', 'documento', 'descricao']) {
  assert.ok(!migration.includes(forbidden), `V043 nao pode persistir ${forbidden}`)
}
assert.ok(openapi.includes('/api/public/wizard-progress/sync:'))
assert.ok(openapi.includes('/api/admin/wizard-progress/dashboard:'))

console.log('WIZARD_PROGRESS_V3_RESULT=OK')
