import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repositoryRoot = path.resolve(frontendRoot, '..')

function read(relativePath) {
  return fs.readFileSync(path.join(repositoryRoot, relativePath), 'utf8')
}

const removedRoutes = [
  'frontend/src/app/(private-routes)/indicacoes/page.tsx',
  'frontend/src/app/(painel-admin)/admin/indicacoes/page.tsx',
]

for (const route of removedRoutes) {
  assert.equal(
    fs.existsSync(path.join(repositoryRoot, route)),
    false,
    `A rota removida nao pode continuar ativa: ${route}`
  )
}

const activeNavigationFiles = [
  'frontend/src/app/(painel-admin)/admin/components/sidebar/sidebar-links.tsx',
  'frontend/src/app/(painel-admin)/admin/components/sidebar/sidebar-utils.ts',
  'frontend/src/components/layout/header-logado.tsx',
  'frontend/src/middleware.ts',
  'frontend/src/app/robots.ts',
  'frontend/next.config.ts',
]

for (const file of activeNavigationFiles) {
  const content = read(file)
  assert.ok(!content.includes('/indicacoes'), `${file} ainda referencia /indicacoes`)
  assert.ok(!content.includes('/admin/indicacoes'), `${file} ainda referencia /admin/indicacoes`)
}

const header = read('frontend/src/components/layout/header-logado.tsx')
assert.ok(!header.includes('Link de Indica'), 'O menu do usuario ainda oferece indicacao')
assert.ok(!header.includes('LinkIcon'), 'O icone exclusivo de indicacao ficou orfao')

const authContext = read('frontend/src/context/AuthContext.tsx')
for (const field of [
  'totalIndicados',
  'creditosIndicacaoGanhos',
  'creditosPorIndicacao',
  'linkIndicacao',
]) {
  assert.ok(!authContext.includes(field), `AuthContext ainda expoe o falso campo ${field}`)
}

const apiContract = read('frontend/src/lib/api-contract.ts')
assert.ok(!apiContract.includes('referrals:'), 'O contrato pendente de indicacoes ficou ativo')

const openApi = read('contracts/openapi/topsdojob-v3-local.yaml')
assert.ok(!openApi.includes('/api/admin/indicacoes'), 'OpenAPI expoe contrato administrativo morto')
assert.ok(!openApi.includes('/api/public/indicacoes'), 'OpenAPI expoe contrato publico morto')

const commercialMigration = read(
  'backend/src/main/resources/db/migration/V012__comercial_suporte.sql'
)
assert.ok(
  commercialMigration.includes("'INDICACAO'"),
  'A origem comercial historica deve permanecer preservada'
)

console.log('INDICACOES_REMOVIDAS_RESULT=OK')
